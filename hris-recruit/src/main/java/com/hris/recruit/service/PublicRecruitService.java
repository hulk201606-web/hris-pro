package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyRequest;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyResponse;
import com.hris.recruit.dto.PublicRecruitDtos.PublicJobItem;
import com.hris.recruit.entity.Application;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.entity.JobOpening;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.entity.StageLog;
import com.hris.recruit.mapper.ApplicationMapper;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.JobOpeningMapper;
import com.hris.recruit.mapper.RequisitionMapper;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.StageLogMapper;
import com.hris.common.util.MaskUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 官网免登录招聘服务（RCS-F-004 / RCS-F-011）。
 *
 * <p>公开职位列表：仅返回 OPEN + published=1 的需求单（租户隔离由网关侧显式 tenantId 参数 +
 * 租户插件双保险，控制器负责把 tenantId 写入 TraceContext）。官网按租户部署，tenantId 由官网
 * 站点配置注入——局限：tenantId 为查询参数而非域名解析，伪造 tenantId 可见该租户公开职位
 * （仅公开信息，不构成越权）。</p>
 *
 * <p>投递链路：限流（控制器层）→ 需求 OPEN+published 校验 → 幂等（同租户+email+同需求已投递
 * 拒绝）→ 黑名单校验（fail-open）→ 落库四件套（candidate/application/stage_log/resume）。</p>
 */
@Service
public class PublicRecruitService {

    /** 投递初始阶段（rcu_application.stage 枚举首阶段）。 */
    public static final String STAGE_APPLIED = "APPLIED";
    /** 投递状态：进行中。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 候选人初始状态。 */
    public static final String CANDIDATE_NEW = "NEW";

    /** 大陆手机号格式。 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 黑名单命中时官网侧的通用拒绝文案（不透露内部原因）。 */
    private static final String PUBLIC_REJECT_MESSAGE = "投递未通过校验，请稍后再试或联系招聘负责人";

    private final RequisitionMapper requisitionMapper;
    private final JobOpeningMapper jobOpeningMapper;
    private final CandidateMapper candidateMapper;
    private final ApplicationMapper applicationMapper;
    private final StageLogMapper stageLogMapper;
    private final ResumeMapper resumeMapper;
    private final BlacklistGuard blacklistGuard;

    public PublicRecruitService(RequisitionMapper requisitionMapper, JobOpeningMapper jobOpeningMapper,
                                CandidateMapper candidateMapper, ApplicationMapper applicationMapper,
                                StageLogMapper stageLogMapper, ResumeMapper resumeMapper,
                                BlacklistGuard blacklistGuard) {
        this.requisitionMapper = requisitionMapper;
        this.jobOpeningMapper = jobOpeningMapper;
        this.candidateMapper = candidateMapper;
        this.applicationMapper = applicationMapper;
        this.stageLogMapper = stageLogMapper;
        this.resumeMapper = resumeMapper;
        this.blacklistGuard = blacklistGuard;
    }

    /** 公开职位列表：OPEN + published=1，title 富化自关联职位（无职位记录为 null）。 */
    public List<PublicJobItem> jobs() {
        List<Requisition> reqs = requisitionMapper.selectList(new LambdaQueryWrapper<Requisition>()
                .eq(Requisition::getStatus, RequisitionService.STATUS_OPEN)
                .eq(Requisition::getPublished, 1)
                .orderByDesc(Requisition::getCreatedAt));
        Map<Long, String> titles = loadTitles(reqs.stream().map(Requisition::getId).toList());
        return reqs.stream()
                .map(r -> new PublicJobItem(r.getId(), r.getReqNo(),
                        titles.get(r.getId()), r.getDeptId(), r.getHeadcount(), r.getCreatedAt()))
                .toList();
    }

    /**
     * 官网投递（事务）。幂等键：租户 + email + 需求（未关闭）。
     * mobile_cipher 语义与现有一致（明文落列，掩码影子列出参），明文永不出参。
     */
    @Transactional(rollbackFor = Exception.class)
    public PublicApplyResponse apply(Long reqId, PublicApplyRequest body) {
        validate(body);

        Requisition req = requisitionMapper.selectById(reqId);
        if (req == null || !RequisitionService.STATUS_OPEN.equals(req.getStatus())) {
            // 不区分「不存在/已关闭」，避免向公网暴露需求状态细节
            throw new BizException(ErrorCode.NOT_FOUND, "职位不存在或已关闭");
        }

        if (alreadyApplied(body.email(), reqId)) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "已投递，请勿重复提交");
        }

        try {
            blacklistGuard.checkOrThrow(null, body.mobile());
        } catch (BizException e) {
            if (BlacklistGuard.HIT_MESSAGE.equals(e.getMessage())) {
                throw new BizException(ErrorCode.PARAM_INVALID, PUBLIC_REJECT_MESSAGE);
            }
            throw e;
        }

        Candidate candidate = new Candidate();
        candidate.setName(body.name());
        candidate.setMobileCipher(body.mobile());
        candidate.setMobileMask(MaskUtil.maskMobile(body.mobile()));
        candidate.setEmail(body.email());
        candidate.setStatus(CANDIDATE_NEW);
        candidateMapper.insert(candidate);

        saveSelfIntroResume(candidate.getId(), body.selfIntro());

        Application application = new Application();
        application.setCandidateId(candidate.getId());
        application.setOpeningId(reqId);
        application.setStage(STAGE_APPLIED);
        application.setStatus(STATUS_ACTIVE);
        applicationMapper.insert(application);

        StageLog stageLog = new StageLog();
        stageLog.setApplicationId(application.getId());
        stageLog.setFromStage(null);
        stageLog.setToStage(STAGE_APPLIED);
        stageLog.setRemark("官网投递");
        stageLogMapper.insert(stageLog);

        return new PublicApplyResponse(application.getId(), "投递成功，请留意邮件通知");
    }

    /** 幂等：同租户下 email 已存在候选人且已对该需求投递 → true。 */
    private boolean alreadyApplied(String email, Long reqId) {
        List<Candidate> sameEmail = candidateMapper.selectList(new LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getEmail, email));
        if (sameEmail.isEmpty()) {
            return false;
        }
        return applicationMapper.selectCount(new LambdaQueryWrapper<Application>()
                .in(Application::getCandidateId, sameEmail.stream().map(Candidate::getId).toList())
                .eq(Application::getOpeningId, reqId)) > 0;
    }

    /** selfIntro 以简历形式落库（rcu_resume.parse_result JSON），无附件。 */
    private void saveSelfIntroResume(Long candidateId, String selfIntro) {
        if (selfIntro == null || selfIntro.isBlank()) {
            return;
        }
        Map<String, String> parsed = new HashMap<>();
        parsed.put("selfIntro", selfIntro);
        parsed.put("source", "PORTAL");
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileId(null);
        resume.setParseResult(toJson(parsed));
        resumeMapper.insert(resume);
    }

    /** 极简 JSON 序列化（值均来自服务端白名单字段，自行转义引号/反斜杠/换行）。 */
    private String toJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"")
                    .append(escape(e.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    private String escape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    /** 入参校验：手机号格式 + selfIntro 内容白名单（emoji/空洞文案禁入）。 */
    private void validate(PublicApplyRequest body) {
        if (body.mobile() == null || !MOBILE_PATTERN.matcher(body.mobile().trim()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "mobile 格式不合法");
        }
        validateSelfIntro(body.selfIntro());
    }

    /**
     * selfIntro 白名单校验：禁止 emoji/特殊符号码点；剔除空白与标点后必须含文字或数字（防空洞刷量）。
     * 文案给出允许内容范围提示。
     */
    private void validateSelfIntro(String intro) {
        if (intro == null || intro.isEmpty()) {
            return;
        }
        boolean hasContent = false;
        for (int i = 0; i < intro.length(); ) {
            int cp = intro.codePointAt(i);
            i += Character.charCount(cp);
            if (isEmojiCodePoint(cp)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "selfIntro 仅支持中英文、数字与常用标点，禁止 emoji 表情符号");
            }
            if (Character.isLetterOrDigit(cp)) {
                hasContent = true;
            }
        }
        if (!hasContent) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "selfIntro 仅支持中英文、数字与常用标点，禁止无实质内容的文案");
        }
    }

    private boolean isEmojiCodePoint(int cp) {
        return cp >= 0x1F000                                  // 主 emoji 区块（含笑脸/手势等）
                || (cp >= 0x2600 && cp <= 0x27BF)             // 杂项符号与装饰符号
                || (cp >= 0x2B00 && cp <= 0x2BFF)             // 箭头/星形等符号区
                || (cp >= 0xFE00 && cp <= 0xFE0F)             // 变体选择符
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF)           // 区域指示符（旗帜）
                || (cp >= 0x1F900 && cp <= 0x1F9FF);          // 补充符号区
    }

    /** 批量取需求关联职位的首个非关闭职位名做富化；缺失不影响列表。 */
    private Map<Long, String> loadTitles(List<Long> reqIds) {
        Map<Long, String> titles = new HashMap<>();
        if (reqIds.isEmpty()) {
            return titles;
        }
        List<JobOpening> openings = jobOpeningMapper.selectList(new LambdaQueryWrapper<JobOpening>()
                .in(JobOpening::getRequisitionId, reqIds)
                .ne(JobOpening::getStatus, "CLOSED"));
        for (JobOpening o : openings) {
            titles.putIfAbsent(o.getRequisitionId(), o.getTitle());
        }
        return titles;
    }
}
