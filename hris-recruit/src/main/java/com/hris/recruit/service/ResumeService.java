package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.config.RecruitProperties;
import com.hris.recruit.dto.ResumeDtos.ResumeCreateRequest;
import com.hris.recruit.dto.ResumeDtos.ResumeResponse;
import com.hris.recruit.dto.ResumeDtos.ResumeUpdateRequest;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.entity.ResumeParse;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.ResumeParseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 简历（Resume）：候选人简历附件与解析结果，按 candidateId 挂载。
 *
 * <p>RCS-F-022 个保合规：创建时按 {@code hris.recruit.resume-retention-days}（缺省 180 天）
 * 计算 retention_until；清理端点逻辑删除已过期简历，先清空文件引用与解析文本再软删。
 * 物理附件删除由 platform 文件服务负责（recruit 侧仅清理引用，见交付报告遗留说明）。
 * 调度不内置 @Scheduled：由运营在 platform 调度中心注册 {@code POST /rcu/resumes/cleanup} 日任务。</p>
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class ResumeService {

    /** 缺省保留天数（配置非法时兜底）。 */
    private static final int DEFAULT_RETENTION_DAYS = 180;

    private final ResumeMapper mapper;
    private final ResumeParseMapper parseMapper;
    private final RecruitProperties properties;

    public ResumeService(ResumeMapper mapper, ResumeParseMapper parseMapper, RecruitProperties properties) {
        this.mapper = mapper;
        this.parseMapper = parseMapper;
        this.properties = properties;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public ResumeResponse get(Long id) {
        return toResponse(requireExists(id));
    }

    /** 分页查询列表。 */
    public PageResult<ResumeResponse> list(long page, long size) {
        IPage<Resume> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ResumeResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建记录（事务）：按租户配置计算保留截止日。 */
    @Transactional(rollbackFor = Exception.class)
    public ResumeResponse create(ResumeCreateRequest req) {
        Resume e = new Resume();
        e.setCandidateId(req.candidateId());
        e.setFileId(req.fileId());
        e.setParseResult(req.parseResult());
        e.setRetentionUntil(LocalDate.now().plusDays(retentionDays()));
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；保留截止日不随内容更新重算。 */
    @Transactional(rollbackFor = Exception.class)
    public ResumeResponse update(Long id, ResumeUpdateRequest req) {
        Resume e = requireExists(id);
        e.setCandidateId(req.candidateId());
        e.setFileId(req.fileId());
        e.setParseResult(req.parseResult());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Resume 不存在");
        }
    }

    /**
     * 清理过期简历（RCS-F-022，事务）：retention_until < today 的记录先清空
     * file_id/parse_result（文件引用与解析文本）再逻辑删除，并逻辑删除关联解析行。
     *
     * @return 本次清理条数，供调度/运营核对
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpired() {
        List<Resume> expired = mapper.selectList(new LambdaQueryWrapper<Resume>()
                .isNotNull(Resume::getRetentionUntil)
                .lt(Resume::getRetentionUntil, LocalDate.now())
                .select(Resume::getId));
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(Resume::getId).toList();
        clearReferences(ids);
        mapper.delete(new LambdaQueryWrapper<Resume>().in(Resume::getId, ids));
        deleteParses(ids);
        return ids.size();
    }

    /**
     * 候选人删除联动（RCS-F-022 删除响应）：逻辑删除其名下全部简历并清理引用，
     * 返回被删简历 ID 明细（空列表表示该候选人无简历）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> deleteByCandidate(Long candidateId) {
        List<Long> ids = mapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getCandidateId, candidateId)
                        .select(Resume::getId))
                .stream().map(Resume::getId).toList();
        if (ids.isEmpty()) {
            return ids;
        }
        clearReferences(ids);
        mapper.delete(new LambdaQueryWrapper<Resume>().in(Resume::getId, ids));
        deleteParses(ids);
        return ids;
    }

    /** 清空文件引用与解析文本（逻辑删前彻底去标识化）。 */
    private void clearReferences(List<Long> ids) {
        mapper.update(null, new LambdaUpdateWrapper<Resume>()
                .in(Resume::getId, ids)
                .set(Resume::getFileId, null)
                .set(Resume::getParseResult, null));
    }

    private void deleteParses(List<Long> resumeIds) {
        parseMapper.delete(new LambdaQueryWrapper<ResumeParse>()
                .in(ResumeParse::getResumeId, resumeIds));
    }

    private int retentionDays() {
        int days = properties.getResumeRetentionDays();
        return days > 0 ? days : DEFAULT_RETENTION_DAYS;
    }

    private Resume requireExists(Long id) {
        Resume e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Resume 不存在");
        }
        return e;
    }

    private ResumeResponse toResponse(Resume e) {
        return new ResumeResponse(e.getId(), e.getCandidateId(), e.getFileId(), e.getParseResult(),
                e.getRetentionUntil(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
