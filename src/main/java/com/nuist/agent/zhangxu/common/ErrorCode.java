package com.nuist.agent.zhangxu.common;

public enum ErrorCode {
    SUCCESS(200, "成功"),
    FAIL(500,"失败"),
    FAQ_NOT_FOUND(404, "问题未找到"),
    PARAMETER_ERROR(100,"参数错误"),
    FAQ_COTENT_EMPTY(103,"问题和答案不能为空"),
    SENSITIVE_WORD_FORMAT_ERROR(104,"敏感词类型必须是deny或allow"),
    USERNAME_EXISTS(1001,"用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1002,"用户名或密码错误"),

    // ===== 智能求职辅助系统：用户与权限（A） =====
    UNAUTHORIZED(401, "未登录或登录已过期，请重新登录"),
    FORBIDDEN(403, "无权访问该资源"),

    // ===== 智能求职辅助系统：简历 / 岗位 / 分析（B） =====
    RESUME_NOT_FOUND(2001, "简历不存在或不属于当前用户"),
    RESUME_CONTENT_EMPTY(2002, "简历正文不能为空"),
    VERSION_NOT_FOUND(2003, "简历版本不存在"),
    POSITION_NOT_FOUND(2004, "岗位信息不存在或不属于当前用户"),
    ANALYSIS_NOT_FOUND(2005, "分析报告不存在"),
    FILE_TYPE_ERROR(2006, "仅支持上传 .txt / .md 文本文件"),
    FILE_EMPTY(2007, "上传文件内容为空或超出大小限制"),

    // ===== 智能求职辅助系统：模拟面试与反馈（C） =====
    INTERVIEW_NOT_FOUND(3001, "模拟面试会话不存在"),
    INTERVIEW_FINISHED(3002, "该模拟面试已结束，无法继续作答"),
    QUESTION_NOT_FOUND(3003, "面试题目不存在"),
    QUESTION_ANSWERED(3004, "该题目已作答，请勿重复提交"),
    FEEDBACK_NOT_FOUND(3005, "面试反馈报告尚未生成"),

    // ===== 智能求职辅助系统：大模型网关（A） =====
    AI_DISABLED(4001, "大模型服务未启用"),
    AI_RATE_LIMITED(4002, "AI 调用过于频繁，请稍后再试"),
    AI_UNAVAILABLE(4003, "AI 服务暂时不可用，已切换本地降级结果"),
    AI_RESPONSE_INVALID(4004, "AI 返回内容无法解析"),
    PROMPT_TEMPLATE_NOT_FOUND(4005, "提示词模板不存在");

    private final Integer code;
    private final String message;
    ErrorCode(Integer code, String message){
        this.code = code;
        this.message = message;
    }
    public Integer getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }

}
