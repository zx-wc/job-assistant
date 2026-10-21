package com.nuist.agent.job.interview;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.InterviewFeedback;
import com.nuist.agent.entity.InterviewQuestion;
import com.nuist.agent.entity.InterviewSession;
import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.ai.AiResults;
import com.nuist.agent.job.ai.LlmGateway;
import com.nuist.agent.job.ai.LlmJsonResult;
import com.nuist.agent.job.ai.LlmUnavailableException;
import com.nuist.agent.job.ai.PromptScene;
import com.nuist.agent.job.common.JsonHelper;
import com.nuist.agent.job.config.JobProperties;
import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;
import com.nuist.agent.job.rag.JobVectorService;
import com.nuist.agent.mapper.InterviewFeedbackMapper;
import com.nuist.agent.mapper.InterviewQuestionMapper;
import com.nuist.agent.mapper.InterviewSessionMapper;
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
import java.util.Locale;
import java.util.Map;

/**
 * 文字模拟面试实现（成员 C）。
 *
 * <p>一条完整链路：开始面试（AI 出题）→ 逐题作答（AI 单题评分）→ 可追问 → 结束（AI 汇总报告）。
 * 任一 AI 环节不可用时都会切换到 {@link FallbackInterviewCoach} 的本地策略并标记 degraded，
 * 保证"AI 挂了也能练习"。</p>
 */
@Service
public class InterviewServiceImpl implements InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String QUESTION_PROMPT = "请围绕以上岗位与简历生成模拟面试问题，严格按 JSON 契约输出。";
    private static final String EVALUATE_PROMPT = "请对以上回答打分并给出点评，严格按 JSON 契约输出。";
    private static final String REPORT_PROMPT = "请基于以上问答记录生成整场面试反馈报告，严格按 JSON 契约输出。";
    private static final String FOLLOW_UP_PROMPT = "请针对以上回答生成一个更深入的追问，严格按 JSON 契约输出。";
    private static final String FALLBACK_MODEL = "local-fallback";
    private static final int MAX_RESUME_CHARS = 5000;
    private static final int MAX_ANSWER_CHARS = 4000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final List<String> DIFFICULTIES = List.of("EASY", "NORMAL", "HARD");

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewFeedbackMapper feedbackMapper;
    private final JobResumeMapper resumeMapper;
    private final JobPositionMapper positionMapper;
    private final LlmGateway llmGateway;
    private final JsonHelper jsonHelper;
    private final FallbackInterviewCoach fallbackCoach;
    private final JobProperties jobProperties;
    /** Milvus 向量检索：出题前召回岗位/简历相关片段，让题目贴合真实材料（RAG） */
    private final JobVectorService vectorService;

    public InterviewServiceImpl(InterviewSessionMapper sessionMapper,
                                InterviewQuestionMapper questionMapper,
                                InterviewFeedbackMapper feedbackMapper,
                                JobResumeMapper resumeMapper,
                                JobPositionMapper positionMapper,
                                LlmGateway llmGateway,
                                JsonHelper jsonHelper,
                                FallbackInterviewCoach fallbackCoach,
                                JobProperties jobProperties,
                                JobVectorService vectorService) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.feedbackMapper = feedbackMapper;
        this.resumeMapper = resumeMapper;
        this.positionMapper = positionMapper;
        this.llmGateway = llmGateway;
        this.jsonHelper = jsonHelper;
        this.fallbackCoach = fallbackCoach;
        this.jobProperties = jobProperties;
        this.vectorService = vectorService;
    }

    // ==================== 开始面试 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewDtos.SessionVO start(Long userId, JobDtos.InterviewStartRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        JobPosition position = requirePosition(userId, request.getPositionId());
        JobResume resume = request.getResumeId() == null ? null : requireResume(userId, request.getResumeId());
        int questionCount = normalizeQuestionCount(request.getQuestionCount());
        String difficulty = normalizeDifficulty(request.getDifficulty());

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(resume == null ? null : resume.getId());
        session.setPositionId(position.getId());
        session.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim()
                : buildTitle(position));
        session.setDifficulty(difficulty);

        List<AiResults.QuestionItem> items;
        String model;
        boolean degraded = false;
        try {
            String knowledge = vectorService.buildContext(userId,
                    resume == null ? null : resume.getId(), position.getId(),
                    buildRetrievalQuery(position), 4);
            LlmJsonResult<AiResults.QuestionListResult> json = llmGateway.chatJsonWithMeta(
                    PromptScene.INTERVIEW_QUESTION,
                    buildQuestionVariables(resume, position, questionCount, difficulty, knowledge),
                    QUESTION_PROMPT, AiResults.QuestionListResult.class);
            items = json == null || json.value() == null ? null : json.value().getQuestions();
            model = json == null ? FALLBACK_MODEL : json.model();
            if (items == null || items.isEmpty()) {
                throw new LlmUnavailableException("AI 未生成任何面试题");
            }
        } catch (LlmUnavailableException e) {
            log.warn("面试出题 AI 不可用,切换本地降级:userId={},err={}", userId, e.getMessage());
            items = fallbackCoach.generateQuestions(session, extractKeywords(position), questionCount).getQuestions();
            model = FALLBACK_MODEL;
            degraded = true;
        } catch (BusinessException e) {
            if (ErrorCode.AI_DISABLED.getCode().equals(e.getCode())) {
                items = fallbackCoach.generateQuestions(session, extractKeywords(position), questionCount).getQuestions();
                model = FALLBACK_MODEL;
                degraded = true;
            } else {
                throw e;
            }
        }

        List<InterviewQuestion> questions = buildQuestionEntities(items, questionCount, degraded);
        session.setTotalQuestions(questions.size());
        session.setAnsweredCount(0);
        session.setStatus(0);
        session.setModel(model);
        session.setDegraded(degraded ? 1 : 0);
        sessionMapper.insert(session);

        List<InterviewQuestion> toInsert = new ArrayList<>();
        int no = 1;
        for (InterviewQuestion question : questions) {
            question.setSessionId(session.getId());
            question.setQuestionNo(no++);
            toInsert.add(question);
        }
        questionMapper.batchInsert(toInsert);
        log.info("开始模拟面试:sessionId={},userId={},题数={},降级={}", session.getId(), userId,
                toInsert.size(), degraded);
        return detail(userId, session.getId());
    }

    // ==================== 详情 ====================

    @Override
    public ViewDtos.SessionVO detail(Long userId, Long sessionId) {
        InterviewSession session = requireSession(userId, sessionId);
        List<InterviewQuestion> questions = questionMapper.selectBySessionId(sessionId);
        ViewDtos.SessionVO vo = toSessionVO(session, questions);
        InterviewFeedback feedback = feedbackMapper.selectBySessionId(sessionId);
        if (feedback != null) {
            vo.setFeedback(toReportVO(feedback));
        }
        return vo;
    }

    // ==================== 逐题作答 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewDtos.AnswerResultVO answer(Long userId, Long sessionId, Integer questionNo, String answerText) {
        InterviewSession session = requireSession(userId, sessionId);
        ensureOngoing(session);
        if (questionNo == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        if (!StringUtils.hasText(answerText)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "回答内容不能为空");
        }
        InterviewQuestion question = questionMapper.selectByQuestionNo(sessionId, questionNo);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        if (question.getStatus() != null && question.getStatus() == 1) {
            throw new BusinessException(ErrorCode.QUESTION_ANSWERED);
        }
        String answer = truncate(answerText.trim(), MAX_ANSWER_CHARS);
        List<String> points = jsonHelper.toStringList(question.getExpectedPoints());

        AiResults.AnswerEvalResult evaluation;
        boolean degraded = false;
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("positionTitle", firstNonBlank(session.getPositionTitle(), session.getTitle()));
            variables.put("question", question.getQuestion());
            variables.put("expectedPoints", points.isEmpty() ? "无" : String.join("；", points));
            variables.put("answer", answer);
            LlmJsonResult<AiResults.AnswerEvalResult> json = llmGateway.chatJsonWithMeta(
                    PromptScene.INTERVIEW_EVALUATE, variables, EVALUATE_PROMPT, AiResults.AnswerEvalResult.class);
            evaluation = json == null ? null : json.value();
            if (evaluation == null) {
                throw new LlmUnavailableException("AI 未返回评分结果");
            }
        } catch (LlmUnavailableException e) {
            log.warn("面试评分 AI 不可用,切换本地降级:sessionId={},questionNo={},err={}",
                    sessionId, questionNo, e.getMessage());
            evaluation = fallbackCoach.evaluate(question.getQuestion(), answer, points);
            degraded = true;
        } catch (BusinessException e) {
            if (ErrorCode.AI_DISABLED.getCode().equals(e.getCode())) {
                evaluation = fallbackCoach.evaluate(question.getQuestion(), answer, points);
                degraded = true;
            } else {
                throw e;
            }
        }

        int score = clampScore(evaluation.getScore());
        questionMapper.updateAnswer(sessionId, questionNo, answer,
                buildFeedbackText(evaluation), score, degraded ? 1 : 0);
        sessionMapper.incrementAnswered(sessionId, userId);

        int answered = questionMapper.countAnswered(sessionId);
        int total = session.getTotalQuestions() == null ? answered : session.getTotalQuestions();

        ViewDtos.AnswerResultVO vo = new ViewDtos.AnswerResultVO();
        vo.setQuestionNo(questionNo);
        vo.setScore(score);
        vo.setComment(evaluation.getComment());
        vo.setHighlights(safeList(evaluation.getHighlights()));
        vo.setImprovements(safeList(evaluation.getImprovements()));
        vo.setAnsweredCount(answered);
        vo.setTotalQuestions(total);
        vo.setAllAnswered(answered >= total);
        vo.setDegraded(degraded);
        vo.setNextQuestion(findNextQuestion(sessionId));
        log.info("提交面试作答:sessionId={},questionNo={},得分={},降级={}", sessionId, questionNo, score, degraded);
        return vo;
    }

    // ==================== 多轮追问 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewDtos.SessionVO followUp(Long userId, Long sessionId, Integer questionNo, String extra) {
        InterviewSession session = requireSession(userId, sessionId);
        ensureOngoing(session);
        if (questionNo == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        InterviewQuestion question = questionMapper.selectByQuestionNo(sessionId, questionNo);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        String answer = StringUtils.hasText(question.getAnswer()) ? question.getAnswer()
                : (StringUtils.hasText(extra) ? extra.trim() : "");
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请先作答后再追问");
        }

        AiResults.FollowUpResult followUp;
        boolean degraded = false;
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("positionTitle", firstNonBlank(session.getPositionTitle(), session.getTitle()));
            variables.put("question", question.getQuestion());
            variables.put("answer", truncate(answer, MAX_ANSWER_CHARS));
            LlmJsonResult<AiResults.FollowUpResult> json = llmGateway.chatJsonWithMeta(
                    PromptScene.INTERVIEW_FOLLOW_UP, variables, FOLLOW_UP_PROMPT, AiResults.FollowUpResult.class);
            followUp = json == null ? null : json.value();
            if (followUp == null || !StringUtils.hasText(followUp.getQuestion())) {
                throw new LlmUnavailableException("AI 未返回追问内容");
            }
        } catch (LlmUnavailableException e) {
            log.warn("面试追问 AI 不可用,切换本地降级:sessionId={},err={}", sessionId, e.getMessage());
            followUp = new AiResults.FollowUpResult();
            followUp.setQuestion("关于第 " + questionNo + " 题的回答，能否补充一个具体细节："
                    + "当时的场景是什么、你采取的关键动作是什么、最终结果用数据怎么衡量？");
            followUp.setExpectedPoints(new ArrayList<>(List.of("是否补充了具体场景", "是否说明关键动作", "是否给出量化结果")));
            degraded = true;
        } catch (BusinessException e) {
            if (ErrorCode.AI_DISABLED.getCode().equals(e.getCode())) {
                followUp = new AiResults.FollowUpResult();
                followUp.setQuestion("关于第 " + questionNo + " 题的回答，能否补充一个具体细节："
                        + "当时的场景是什么、你采取的关键动作是什么、最终结果用数据怎么衡量？");
                followUp.setExpectedPoints(new ArrayList<>(List.of("是否补充了具体场景", "是否说明关键动作", "是否给出量化结果")));
                degraded = true;
            } else {
                throw e;
            }
        }

        Integer maxNo = questionMapper.selectMaxQuestionNo(sessionId);
        int newNo = maxNo == null ? 1 : maxNo + 1;
        InterviewQuestion followUpQuestion = new InterviewQuestion();
        followUpQuestion.setSessionId(sessionId);
        followUpQuestion.setQuestionNo(newNo);
        followUpQuestion.setQuestion(truncate(followUp.getQuestion(), 1000));
        followUpQuestion.setQuestionType("追问");
        followUpQuestion.setExpectedPoints(jsonHelper.toJson(safeList(followUp.getExpectedPoints())));
        followUpQuestion.setDegraded(degraded ? 1 : 0);
        questionMapper.insert(followUpQuestion);
        sessionMapper.updateTotalQuestions(sessionId, userId, newNo);
        log.info("生成追问:sessionId={},来源题={},新题号={},降级={}", sessionId, questionNo, newNo, degraded);
        return detail(userId, sessionId);
    }

    // ==================== 结束与报告 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewDtos.ReportVO finish(Long userId, Long sessionId) {
        InterviewSession session = requireSession(userId, sessionId);
        InterviewFeedback existing = feedbackMapper.selectBySessionId(sessionId);
        if (existing != null) {
            return toReportVO(existing);
        }
        List<InterviewQuestion> questions = questionMapper.selectBySessionId(sessionId);
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "本场面试没有题目，无法生成报告");
        }
        long answered = questions.stream().filter(q -> q.getStatus() != null && q.getStatus() == 1).count();
        if (answered == 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请至少完成一道题后再生成报告");
        }

        AiResults.InterviewReportResult result;
        String model;
        boolean degraded = false;
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("positionTitle", firstNonBlank(session.getPositionTitle(), session.getTitle()));
            variables.put("company", firstNonBlank(session.getCompany(), "未填写公司"));
            variables.put("qa", buildQaText(questions));
            LlmJsonResult<AiResults.InterviewReportResult> json = llmGateway.chatJsonWithMeta(
                    PromptScene.INTERVIEW_REPORT, variables, REPORT_PROMPT, AiResults.InterviewReportResult.class);
            result = json == null ? null : json.value();
            model = json == null ? FALLBACK_MODEL : json.model();
            if (result == null) {
                throw new LlmUnavailableException("AI 未返回反馈报告");
            }
        } catch (LlmUnavailableException e) {
            log.warn("面试报告 AI 不可用,切换本地降级:sessionId={},err={}", sessionId, e.getMessage());
            result = fallbackCoach.report(session, questions);
            model = FALLBACK_MODEL;
            degraded = true;
        } catch (BusinessException e) {
            if (ErrorCode.AI_DISABLED.getCode().equals(e.getCode())) {
                result = fallbackCoach.report(session, questions);
                model = FALLBACK_MODEL;
                degraded = true;
            } else {
                throw e;
            }
        }

        int overall = result.getOverallScore() == null ? averageScore(questions) : clampScore(result.getOverallScore());
        InterviewFeedback feedback = new InterviewFeedback();
        feedback.setSessionId(sessionId);
        feedback.setOverallScore(overall);
        feedback.setSummary(truncate(result.getSummary(), 2000));
        feedback.setStrengths(jsonHelper.toJson(safeList(result.getStrengths())));
        feedback.setWeaknesses(jsonHelper.toJson(safeList(result.getWeaknesses())));
        feedback.setSuggestions(jsonHelper.toJson(safeList(result.getSuggestions())));
        feedback.setDimensionScores(jsonHelper.toJson(result.getDimensionScores() == null
                ? new LinkedHashMap<String, Integer>() : result.getDimensionScores()));
        feedback.setModel(model);
        feedback.setDegraded(degraded ? 1 : 0);
        feedbackMapper.insert(feedback);
        sessionMapper.finish(sessionId, userId, overall);
        log.info("生成面试反馈报告:sessionId={},综合得分={},降级={}", sessionId, overall, degraded);
        return toReportVO(feedback);
    }

    @Override
    public ViewDtos.ReportVO report(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        InterviewFeedback feedback = feedbackMapper.selectBySessionId(sessionId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        return toReportVO(feedback);
    }

    // ==================== 记录回顾 ====================

    @Override
    public ViewDtos.PageResult<ViewDtos.SessionVO> page(Long userId, Integer status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        List<InterviewSession> sessions = sessionMapper.selectByPage(userId, status, offset, safeSize);
        List<ViewDtos.SessionVO> list = new ArrayList<>();
        for (InterviewSession session : sessions) {
            list.add(toSessionVO(session, null));
        }
        return ViewDtos.PageResult.of(list, sessionMapper.countByUser(userId, status), safePage, safeSize);
    }

    @Override
    public int countByUser(Long userId) {
        return sessionMapper.countByUser(userId, null);
    }

    @Override
    public int countOngoing(Long userId) {
        return sessionMapper.countOngoing(userId);
    }

    // ==================== 内部方法 ====================

    private Map<String, Object> buildQuestionVariables(JobResume resume, JobPosition position,
                                                       int questionCount, String difficulty, String knowledge) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("resume", resume == null || !StringUtils.hasText(resume.getContent())
                ? "（候选人未提供简历，按应届生通用背景出题）" : truncate(resume.getContent(), MAX_RESUME_CHARS));
        variables.put("positionTitle", firstNonBlank(position.getPosition(), "目标岗位"));
        variables.put("company", firstNonBlank(position.getCompany(), "未填写公司"));
        variables.put("jobDesc", firstNonBlank(position.getJobDesc(), "岗位职责未填写"));
        variables.put("requirements", firstNonBlank(position.getRequirements(), "任职要求未填写"));
        variables.put("questionCount", questionCount);
        variables.put("difficulty", difficulty);
        variables.put("knowledge", knowledge == null ? "(无)" : knowledge);
        return variables;
    }

    /** 构造向量检索 query：岗位名称 + 职责 + 任职要求 */
    private String buildRetrievalQuery(JobPosition position) {
        String query = String.join("\n", position.getPosition() == null ? "" : position.getPosition(),
                position.getJobDesc() == null ? "" : position.getJobDesc(),
                position.getRequirements() == null ? "" : position.getRequirements());
        return query.length() > 1500 ? query.substring(0, 1500) : query;
    }

    /** 清洗 AI 返回的题目：过滤空题、去重、按需求数量截断 */
    private List<InterviewQuestion> buildQuestionEntities(List<AiResults.QuestionItem> items,
                                                          int questionCount, boolean degraded) {
        List<InterviewQuestion> questions = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        if (items != null) {
            for (AiResults.QuestionItem item : items) {
                if (item == null || !StringUtils.hasText(item.getQuestion())) {
                    continue;
                }
                String text = item.getQuestion().trim();
                if (seen.contains(text)) {
                    continue;
                }
                seen.add(text);
                InterviewQuestion question = new InterviewQuestion();
                question.setQuestion(truncate(text, 1000));
                question.setQuestionType(normalizeType(item.getType()));
                question.setExpectedPoints(jsonHelper.toJson(safeList(item.getExpectedPoints())));
                question.setStatus(0);
                question.setDegraded(degraded ? 1 : 0);
                questions.add(question);
                if (questions.size() >= questionCount) {
                    break;
                }
            }
        }
        return questions;
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "技能";
        }
        String value = type.trim();
        for (String allowed : List.of("技能", "项目", "行为", "岗位匹配", "追问")) {
            if (value.contains(allowed)) {
                return allowed;
            }
        }
        return "技能";
    }

    private void ensureOngoing(InterviewSession session) {
        if (session.getStatus() != null && session.getStatus() == 1) {
            throw new BusinessException(ErrorCode.INTERVIEW_FINISHED);
        }
    }

    private InterviewSession requireSession(Long userId, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND);
        }
        InterviewSession session = sessionMapper.selectById(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND);
        }
        return session;
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

    private JobResume requireResume(Long userId, Long resumeId) {
        JobResume resume = resumeMapper.selectById(resumeId, userId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        return resume;
    }

    private ViewDtos.QuestionVO findNextQuestion(Long sessionId) {
        List<InterviewQuestion> questions = questionMapper.selectBySessionId(sessionId);
        for (InterviewQuestion question : questions) {
            if (question.getStatus() == null || question.getStatus() == 0) {
                return toQuestionVO(question);
            }
        }
        return null;
    }

    private ViewDtos.SessionVO toSessionVO(InterviewSession session, List<InterviewQuestion> questions) {
        ViewDtos.SessionVO vo = new ViewDtos.SessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setDifficulty(session.getDifficulty());
        vo.setStatus(session.getStatus());
        vo.setTotalQuestions(session.getTotalQuestions());
        vo.setAnsweredCount(session.getAnsweredCount());
        vo.setOverallScore(session.getOverallScore());
        vo.setResumeId(session.getResumeId());
        vo.setResumeTitle(session.getResumeTitle());
        vo.setPositionId(session.getPositionId());
        vo.setPositionTitle(session.getPositionTitle());
        vo.setCompany(session.getCompany());
        vo.setDegraded(session.getDegraded() != null && session.getDegraded() == 1);
        vo.setCreateTime(session.getCreateTime() == null ? null : session.getCreateTime().format(TIME_FORMAT));
        vo.setEndTime(session.getEndTime() == null ? null : session.getEndTime().format(TIME_FORMAT));
        if (questions != null) {
            List<ViewDtos.QuestionVO> questionVOs = new ArrayList<>();
            for (InterviewQuestion question : questions) {
                questionVOs.add(toQuestionVO(question));
            }
            vo.setQuestions(questionVOs);
        }
        return vo;
    }

    private ViewDtos.QuestionVO toQuestionVO(InterviewQuestion question) {
        ViewDtos.QuestionVO vo = new ViewDtos.QuestionVO();
        vo.setId(question.getId());
        vo.setQuestionNo(question.getQuestionNo());
        vo.setQuestion(question.getQuestion());
        vo.setType(question.getQuestionType());
        vo.setExpectedPoints(jsonHelper.toStringList(question.getExpectedPoints()));
        vo.setAnswer(question.getAnswer());
        vo.setFeedback(question.getAnswerFeedback());
        vo.setScore(question.getScore());
        vo.setStatus(question.getStatus());
        vo.setDegraded(question.getDegraded() != null && question.getDegraded() == 1);
        return vo;
    }

    private ViewDtos.ReportVO toReportVO(InterviewFeedback feedback) {
        ViewDtos.ReportVO vo = new ViewDtos.ReportVO();
        vo.setSessionId(feedback.getSessionId());
        vo.setOverallScore(feedback.getOverallScore());
        vo.setSummary(feedback.getSummary());
        vo.setStrengths(jsonHelper.toStringList(feedback.getStrengths()));
        vo.setWeaknesses(jsonHelper.toStringList(feedback.getWeaknesses()));
        vo.setSuggestions(jsonHelper.toStringList(feedback.getSuggestions()));
        vo.setDimensionScores(jsonHelper.toIntMap(feedback.getDimensionScores()));
        vo.setModel(feedback.getModel());
        vo.setDegraded(feedback.getDegraded() != null && feedback.getDegraded() == 1);
        vo.setCreateTime(feedback.getCreateTime() == null ? null : feedback.getCreateTime().format(TIME_FORMAT));
        return vo;
    }

    /** 单题点评文本（点评 + 亮点 + 改进建议），便于历史记录直接展示 */
    private String buildFeedbackText(AiResults.AnswerEvalResult evaluation) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(evaluation.getComment())) {
            builder.append(evaluation.getComment().trim());
        }
        List<String> highlights = safeList(evaluation.getHighlights());
        if (!highlights.isEmpty()) {
            builder.append("\n亮点：").append(String.join("；", highlights));
        }
        List<String> improvements = safeList(evaluation.getImprovements());
        if (!improvements.isEmpty()) {
            builder.append("\n改进：").append(String.join("；", improvements));
        }
        return truncate(builder.toString(), 2000);
    }

    /** 组装整场问答文本供报告使用 */
    private String buildQaText(List<InterviewQuestion> questions) {
        StringBuilder builder = new StringBuilder();
        for (InterviewQuestion question : questions) {
            builder.append("第").append(question.getQuestionNo()).append("题（")
                    .append(question.getQuestionType()).append("）：")
                    .append(question.getQuestion()).append('\n');
            builder.append("要点：").append(String.join("；", jsonHelper.toStringList(question.getExpectedPoints())))
                    .append('\n');
            builder.append("回答：").append(StringUtils.hasText(question.getAnswer())
                    ? truncate(question.getAnswer(), 1200) : "（未作答）").append('\n');
            builder.append("单题得分：").append(question.getScore() == null ? 0 : question.getScore()).append("\n\n");
        }
        return truncate(builder.toString(), 12000);
    }

    /** 从岗位描述中提取关键词（降级出题使用） */
    private List<String> extractKeywords(JobPosition position) {
        List<String> keywords = new ArrayList<>();
        String text = (nullToEmpty(position.getRequirements()) + "\n" + nullToEmpty(position.getJobDesc())).toLowerCase(Locale.ROOT);
        for (String keyword : List.of("java", "spring boot", "mysql", "redis", "linux", "docker",
                "kafka", "微服务", "高并发", "索引", "缓存", "并发", "算法", "数据结构",
                "jvm", "分布式", "消息队列", "单元测试", "vue", "react", "python")) {
            if (text.contains(keyword)) {
                keywords.add(keyword.toUpperCase(Locale.ROOT).equals("JAVA") ? "Java"
                        : keyword.substring(0, 1).toUpperCase(Locale.ROOT) + keyword.substring(1));
            }
        }
        if (keywords.isEmpty()) {
            keywords.add(firstNonBlank(position.getPosition(), "技术基础"));
        }
        return keywords;
    }

    private int normalizeQuestionCount(Integer count) {
        int defaultValue = jobProperties.getInterview().getDefaultQuestionCount();
        int max = jobProperties.getInterview().getMaxQuestionCount();
        if (count == null || count <= 0) {
            return Math.max(1, Math.min(defaultValue, max));
        }
        return Math.max(1, Math.min(count, max));
    }

    private String normalizeDifficulty(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return "NORMAL";
        }
        String value = difficulty.trim().toUpperCase(Locale.ROOT);
        return DIFFICULTIES.contains(value) ? value : "NORMAL";
    }

    private String buildTitle(JobPosition position) {
        String company = firstNonBlank(position.getCompany(), "");
        String title = firstNonBlank(position.getPosition(), "目标岗位");
        return StringUtils.hasText(company) ? company + "-" + title + " 模拟面试" : title + " 模拟面试";
    }

    private int averageScore(List<InterviewQuestion> questions) {
        if (questions.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (InterviewQuestion question : questions) {
            total += question.getScore() == null ? 0 : question.getScore();
        }
        return (int) Math.round((double) total / questions.size());
    }

    private int clampScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private List<String> safeList(List<String> list) {
        List<String> result = new ArrayList<>();
        if (list != null) {
            for (String item : list) {
                if (StringUtils.hasText(item)) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
