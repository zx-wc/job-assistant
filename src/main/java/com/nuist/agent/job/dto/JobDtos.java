package com.nuist.agent.job.dto;

import lombok.Data;

/**
 * 求职业务请求 DTO（成员 B/C：简历、岗位、分析、面试）。
 */
public final class JobDtos {

    private JobDtos() {
    }

    /** 简历新增/编辑请求 */
    @Data
    public static class ResumeSaveRequest {
        private String title;
        private String candidateName;
        private String targetPosition;
        private String summary;
        private String content;
        private Integer status;
    }

    /** 手动保存版本请求 */
    @Data
    public static class VersionSaveRequest {
        private String changeNote;
    }

    /** 岗位新增/编辑请求 */
    @Data
    public static class PositionSaveRequest {
        private String company;
        private String position;
        private String city;
        private String salaryRange;
        private String jobDesc;
        private String requirements;
        private String sourceUrl;
        private Integer status;
    }

    /** 创建简历岗位分析请求 */
    @Data
    public static class AnalysisCreateRequest {
        private Long resumeId;
        private Long positionId;
    }

    /** 开始模拟面试请求 */
    @Data
    public static class InterviewStartRequest {
        private Long resumeId;
        private Long positionId;
        private String title;
        private String difficulty;
        private Integer questionCount;
    }

    /** 提交单题作答请求 */
    @Data
    public static class InterviewAnswerRequest {
        private Integer questionNo;
        private String answer;
    }

    /** 追问请求 */
    @Data
    public static class FollowUpRequest {
        private Integer questionNo;
        private String extra;
    }

    /** 提示词模板保存请求（管理员） */
    @Data
    public static class PromptTemplateSaveRequest {
        private String name;
        private String content;
        private String description;
        private Boolean enabled;
    }
}
