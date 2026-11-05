package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.entity.OrgGradeVersion;
import com.hris.org.entity.OrgPositionVersion;
import com.hris.org.enums.OrgVersionStatus;
import com.hris.org.mapper.OrgGradeVersionMapper;
import com.hris.org.mapper.OrgPositionVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 组织版本状态机（批 11 #23 / ORG-F-004）：DRAFT → ACTIVE → SUPERSEDED。
 *
 * <p>状态与生效日按批次冗余存于 org_position_version / org_grade_version 两表同批次的
 * 每一行（同版本号同值），因此状态流转以 version_no 为粒度批量更新。租户隔离由
 * MyBatis-Plus 租户插件在 UpdateWrapper 上自动追加 tenant_id 条件。</p>
 *
 * <p>唯一 ACTIVE 约束：激活时先把本租户其它 ACTIVE 批次置 SUPERSEDED，再置目标批次 ACTIVE，
 * 因此「回滚 = 激活历史版本」天然复用同一流转（原 ACTIVE 自动降为 SUPERSEDED）。</p>
 *
 * <p>生效日语义：创建默认 DRAFT；{@link #activateDue(LocalDate)} 只激活
 * effective_from &lt;= 今日的批次（调度中心日任务经
 * {@code POST /api/v1/org/versions/activate-due} 触发）；手动 activate 为管理端显式
 * 提前生效，不受生效日限制。</p>
 */
@Service
public class OrgVersionStateService {

    private static final Logger log = LoggerFactory.getLogger(OrgVersionStateService.class);

    private final OrgPositionVersionMapper positionVersionMapper;
    private final OrgGradeVersionMapper gradeVersionMapper;

    public OrgVersionStateService(OrgPositionVersionMapper positionVersionMapper,
                                  OrgGradeVersionMapper gradeVersionMapper) {
        this.positionVersionMapper = positionVersionMapper;
        this.gradeVersionMapper = gradeVersionMapper;
    }

    /** 版本批次是否存在（岗位或职级任一维度有行）。 */
    public boolean exists(String versionNo) {
        return !positionRows(versionNo).isEmpty() || !gradeRows(versionNo).isEmpty();
    }

    /**
     * 批次当前状态。历史遗留行（本功能上线前创建）status 为空，按 ACTIVE 处理以保持
     * 「默认可见」的既有行为。
     */
    public OrgVersionStatus statusOf(String versionNo) {
        List<OrgPositionVersion> positions = positionRows(versionNo);
        if (!positions.isEmpty()) {
            return normalize(positions.get(0).getStatus());
        }
        List<OrgGradeVersion> grades = gradeRows(versionNo);
        if (!grades.isEmpty()) {
            return normalize(grades.get(0).getStatus());
        }
        throw new BizException(ErrorCode.NOT_FOUND, "组织版本不存在");
    }

    /** 批次生效日（岗位维度优先，取不到返回 null 表示立即生效）。 */
    public LocalDate effectiveFromOf(String versionNo) {
        List<OrgPositionVersion> positions = positionRows(versionNo);
        if (!positions.isEmpty()) {
            return positions.get(0).getEffectiveFrom();
        }
        List<OrgGradeVersion> grades = gradeRows(versionNo);
        return grades.isEmpty() ? null : grades.get(0).getEffectiveFrom();
    }

    /**
     * 激活指定批次：本租户其它 ACTIVE 批次置 SUPERSEDED，目标批次置 ACTIVE。
     * 已 ACTIVE 的批次重复调用为幂等（仅刷新状态，不产生副作用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgVersionStatus activate(String versionNo) {
        if (!exists(versionNo)) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织版本不存在");
        }
        supersedeOtherActive(versionNo);
        markStatus(versionNo, OrgVersionStatus.ACTIVE);
        log.info("组织版本激活: versionNo={}", versionNo);
        return OrgVersionStatus.ACTIVE;
    }

    /**
     * 到期激活（调度中心日任务调用，按租户执行）：把所有 DRAFT 且
     * effective_from &lt;= today 的批次逐个激活。
     *
     * @param today 判定基准日（null 取系统当天）
     * @return 本租户本次激活的批次数
     */
    @Transactional(rollbackFor = Exception.class)
    public int activateDue(LocalDate today) {
        LocalDate date = today == null ? LocalDate.now() : today;
        Map<LocalDate, Set<String>> due = new TreeMap<>();
        positionVersionMapper.selectList(new LambdaQueryWrapper<OrgPositionVersion>()
                        .eq(OrgPositionVersion::getStatus, OrgVersionStatus.DRAFT.name())
                        .isNotNull(OrgPositionVersion::getEffectiveFrom)
                        .le(OrgPositionVersion::getEffectiveFrom, date))
                .forEach(r -> due.computeIfAbsent(r.getEffectiveFrom(), k -> new LinkedHashSet<>())
                        .add(r.getVersionNo()));
        gradeVersionMapper.selectList(new LambdaQueryWrapper<OrgGradeVersion>()
                        .eq(OrgGradeVersion::getStatus, OrgVersionStatus.DRAFT.name())
                        .isNotNull(OrgGradeVersion::getEffectiveFrom)
                        .le(OrgGradeVersion::getEffectiveFrom, date))
                .forEach(r -> due.computeIfAbsent(r.getEffectiveFrom(), k -> new LinkedHashSet<>())
                        .add(r.getVersionNo()));
        // 按生效日升序激活，生效日最晚的批次成为最终 ACTIVE
        int activated = due.values().stream().mapToInt(set -> {
            set.forEach(this::activate);
            return set.size();
        }).sum();
        if (activated > 0) {
            log.info("组织版本到期激活: date={}, activated={}", date, activated);
        }
        return activated;
    }

    /** 把目标批次以外的 ACTIVE 行置 SUPERSEDED（两维度）。 */
    private void supersedeOtherActive(String keepVersionNo) {
        positionVersionMapper.update(null, new UpdateWrapper<OrgPositionVersion>()
                .eq("status", OrgVersionStatus.ACTIVE.name())
                .ne("version_no", keepVersionNo)
                .set("status", OrgVersionStatus.SUPERSEDED.name()));
        gradeVersionMapper.update(null, new UpdateWrapper<OrgGradeVersion>()
                .eq("status", OrgVersionStatus.ACTIVE.name())
                .ne("version_no", keepVersionNo)
                .set("status", OrgVersionStatus.SUPERSEDED.name()));
    }

    private void markStatus(String versionNo, OrgVersionStatus status) {
        positionVersionMapper.update(null, new UpdateWrapper<OrgPositionVersion>()
                .eq("version_no", versionNo)
                .set("status", status.name()));
        gradeVersionMapper.update(null, new UpdateWrapper<OrgGradeVersion>()
                .eq("version_no", versionNo)
                .set("status", status.name()));
    }

    private List<OrgPositionVersion> positionRows(String versionNo) {
        return positionVersionMapper.selectList(new LambdaQueryWrapper<OrgPositionVersion>()
                .eq(OrgPositionVersion::getVersionNo, versionNo));
    }

    private List<OrgGradeVersion> gradeRows(String versionNo) {
        return gradeVersionMapper.selectList(new LambdaQueryWrapper<OrgGradeVersion>()
                .eq(OrgGradeVersion::getVersionNo, versionNo));
    }

    private OrgVersionStatus normalize(String status) {
        OrgVersionStatus parsed = OrgVersionStatus.from(status);
        return parsed == null ? OrgVersionStatus.ACTIVE : parsed;
    }
}
