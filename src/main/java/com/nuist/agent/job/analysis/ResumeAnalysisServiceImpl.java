package com.nuist.agent.job.analysis;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.JobAnalysis;
import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.ai.AiResults;
import com.nuist.agent.job.ai.LlmGateway;
import com.nuist.agent.job.ai.LlmJsonResult;
import com.nuist.agent.job.ai.LlmUnavailableException;
import com.nuist.agent.job.ai.PromptScene;
import com.nuist.agent.job.common.JsonHelper;
import com.nuist.agent.job.dto.ViewDtos;
import com.nuist.agent.job.rag.JobVectorService;
import com.nuist.agent.mapper.JobAnalysisMapper;
import com.nuist.agent.mapper.JobPositionMapper;
import com.nuist.agent.mapper.JobResumeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历与岗位分析实现（成员 B）。
 *
 * <p>流程：校验简历/岗位归属 → 组装 Prompt 变量 → 调用 AI 网关（结构化 JSON）
 * → 结构化存储（列表/评分以 JSON 落库）→ 返回报告 VO；
 * AI 不可用时自动切换 {@link AnalysisFallbackAnalyzer} 的本地规则分析并标记 degraded。</p>
 */
@Service
public class ResumeAnalysisServiceImpl implements ResumeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String USER_PROMPT = "请分析以上简历与目标岗位，严格按 JSON 契约输出分析结果。";
    private static final int MAX_RESUME_CHARS = 6000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String FALLBACK_MODEL = "local-fallback";

    private final JobResumeMapper resumeMapper;
    private final JobPositionMapper positionMapper;
    private final JobAnalysisMapper analysisMapper;
    private final LlmGateway llmGateway;
    private final JsonHelper jsonHelper;
    private final AnalysisFallbackAnalyzer fallbackAnalyzer;
    /** Milvus 向量检索：分析前先召回岗位/简历相关片段，作为提示词上下文（RAG） */
    private final JobVectorService vectorService;

    public ResumeAnalysisServiceImpl(JobResumeMapper resumeMapper,
                                     JobPositionMapper positionMapper,
                                     JobAnalysisMapper analysisMapper,
                                     LlmGateway llmGateway,
                                     JsonHelper jsonHelper,
                                     AnalysisFallbackAnalyzer fallbackAnalyzer,
                                     JobVectorService vectorService) {
        this.resumeMapper = resumeMapper;
        this.positionMapper = positionMapper;
        this.analysisMapper = analysisMapper;
        this.llmGateway = llmGateway;
        this.jsonHelper = jsonHelper;
        this.fallbackAnalyzer = fallbackAnalyzer;
        this.vectorService = vectorService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewDtos.AnalysisVO analyze(Long userId, Long resumeId, Long positionId) {
        JobResume resume = requireResume(userId, resumeId);
        JobPosition position = requirePosition(userId, positionId);

        // RAG：用岗位要求做 query，从 Milvus 召回该岗位与简历的相关片段
        String knowledge = vectorService.buildContext(userId, resume.getId(), position.getId(),
                buildRetrievalQuery(position), 4);

        AiResults.ResumeAnalysisResult result;
        String model;
        boolean degraded = false;
        try {
            LlmJsonResult<AiResults.ResumeAnalysisResult> json = llmGateway.chatJsonWithMeta(
                    PromptScene.RESUME_ANALYSIS, buildVariables(resume, position, knowledge), USER_PROMPT,
                    AiResults.ResumeAnalysisResult.class);
            result = json == null ? null : json.value();
            model = json == null ? FALLBACK_MODEL : json.model();
            if (result == null) {
                throw new LlmUnavailableException("AI 返回空分析结果");
            }
        } catch (LlmUnavailableException e) {
            log.warn("简历分析 AI 不可用,切换本地降级:userId={},resumeId={},err={}", userId, resumeId, e.getMessage());
            result = fallbackAnalyzer.analyze(resume, position);
            model = FALLBACK_MODEL;
            degraded = true;
        } catch (BusinessException e) {
            // 关闭 AI 时也走本地降级，其余业务异常（如限流）继续抛给前端
            if (ErrorCode.AI_DISABLED.getCode().equals(e.getCode())) {
                result = fallbackAnalyzer.analyze(resume, position);
                model = FALLBACK_MODEL;
                degraded = true;
            } else {
                throw e;
            }
        }

        JobAnalysis entity = new JobAnalysis();
        entity.setUserId(userId);
        entity.setResumeId(resume.getId());
        entity.setPositionId(position.getId());
        entity.setMatchScore(clampScore(result.getMatchScore()));
        entity.setSummary(truncate(result.getSummary(), 2000));
        entity.setStrengths(jsonHelper.toJson(result.getStrengths()));
        entity.setWeaknesses(jsonHelper.toJson(result.getWeaknesses()));
        entity.setSuggestions(jsonHelper.toJson(result.getSuggestions()));
        entity.setMissingSkills(jsonHelper.toJson(result.getMissingSkills()));
        entity.setInterviewFocus(jsonHelper.toJson(result.getInterviewFocus()));
        entity.setDimensionScores(jsonHelper.toJson(result.getDimensionScores() == null
                ? new LinkedHashMap<String, Integer>() : result.getDimensionScores()));
        entity.setModel(model);
        entity.setDegraded(degraded ? 1 : 0);
        analysisMapper.insert(entity);

        log.info("简历分析完成:analysisId={},userId={},匹配度={},降级={}", entity.getId(), userId,
                entity.getMatchScore(), degraded);
        ViewDtos.AnalysisVO vo = toVO(entity);
        vo.setResumeTitle(resume.getTitle());
        vo.setPositionTitle(position.getPosition());
        vo.setCompany(position.getCompany());
        return vo;
    }

    @Override
    public ViewDtos.AnalysisVO detail(Long userId, Long analysisId) {
        JobAnalysis entity = analysisMapper.selectById(analysisId, userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public ViewDtos.PageResult<ViewDtos.AnalysisVO> page(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        List<JobAnalysis> entities = analysisMapper.selectByPage(userId, offset, safeSize);
        List<ViewDtos.AnalysisVO> list = new ArrayList<>();
        for (JobAnalysis entity : entities) {
            list.add(toVO(entity));
        }
        return ViewDtos.PageResult.of(list, analysisMapper.countByUser(userId), safePage, safeSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long analysisId) {
        int rows = analysisMapper.deleteById(analysisId, userId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND);
        }
        log.info("删除分析报告:userId={},analysisId={}", userId, analysisId);
    }

    @Override
    public int countByUser(Long userId) {
        return analysisMapper.countByUser(userId);
    }

    /** 组装提示词变量（简历正文截断，避免超长 token；knowledge 为 Milvus 检索到的片段） */
    private Map<String, Object> buildVariables(JobResume resume, JobPosition position, String knowledge) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("resume", truncate(resume.getContent(), MAX_RESUME_CHARS));
        variables.put("positionTitle", firstNonBlank(position.getPosition(), resume.getTargetPosition(), "未填写岗位"));
        variables.put("company", firstNonBlank(position.getCompany(), "未填写公司"));
        variables.put("jobDesc", firstNonBlank(position.getJobDesc(), "岗位职责未填写"));
        variables.put("requirements", firstNonBlank(position.getRequirements(), "任职要求未填写"));
        variables.put("knowledge", knowledge);
        return variables;
    }

    /** 构造向量检索 query：岗位名称 + 职责 + 任职要求 */
    private String buildRetrievalQuery(JobPosition position) {
        String query = String.join("\n",
                nullSafe(position.getPosition()), nullSafe(position.getJobDesc()), nullSafe(position.getRequirements()));
        return query.length() > 1500 ? query.substring(0, 1500) : query;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private ViewDtos.AnalysisVO toVO(JobAnalysis entity) {
        ViewDtos.AnalysisVO vo = new ViewDtos.AnalysisVO();
        vo.setId(entity.getId());
        vo.setResumeId(entity.getResumeId());
        vo.setResumeTitle(entity.getResumeTitle());
        vo.setPositionId(entity.getPositionId());
        vo.setPositionTitle(entity.getPositionTitle());
        vo.setCompany(entity.getCompany());
        vo.setMatchScore(entity.getMatchScore() == null ? 0 : entity.getMatchScore());
        vo.setSummary(entity.getSummary());
        vo.setStrengths(jsonHelper.toStringList(entity.getStrengths()));
        vo.setWeaknesses(jsonHelper.toStringList(entity.getWeaknesses()));
        vo.setSuggestions(jsonHelper.toStringList(entity.getSuggestions()));
        vo.setMissingSkills(jsonHelper.toStringList(entity.getMissingSkills()));
        vo.setInterviewFocus(jsonHelper.toStringList(entity.getInterviewFocus()));
        vo.setDimensionScores(jsonHelper.toIntMap(entity.getDimensionScores()));
        vo.setModel(entity.getModel());
        vo.setDegraded(entity.getDegraded() != null && entity.getDegraded() == 1);
        vo.setCreateTime(entity.getCreateTime() == null ? null : entity.getCreateTime().format(TIME_FORMAT));
        return vo;
    }

    private JobResume requireResume(Long userId, Long resumeId) {
        if (resumeId == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        JobResume resume = resumeMapper.selectById(resumeId, userId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        return resume;
    }

    private JobPosition requirePosition(Long userId, Long positionId) {
        if (positionId == null) {
            throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
        }
        JobPosition position = positionMapper.selectById(positionId, userId);
        if (position == null) {
            throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
        }
        return position;
    }

    private int clampScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
