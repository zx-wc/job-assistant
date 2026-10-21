package com.nuist.agent.zhangxu.ai;

/**
 * 大模型调用场景（成员 A：统一网关）。
 * 每个场景对应一个提示词模板，模板可被数据库 t_llm_prompt_template 覆盖。
 */
public enum PromptScene {

    /** 简历与岗位分析：输出结构化改进建议 */
    RESUME_ANALYSIS("resume-analysis", "简历与岗位分析", "prompts/job/resume-analysis.st"),

    /** 模拟面试出题：围绕目标岗位生成整套问题 */
    INTERVIEW_QUESTION("interview-question", "模拟面试出题", "prompts/job/interview-question.st"),

    /** 面试回答评分：单题打分与点评 */
    INTERVIEW_EVALUATE("interview-evaluate", "面试回答评分", "prompts/job/interview-evaluate.st"),

    /** 面试反馈报告：整场汇总 */
    INTERVIEW_REPORT("interview-report", "面试反馈报告", "prompts/job/interview-report.st"),

    /** 面试追问：对含糊回答做一次深入追问 */
    INTERVIEW_FOLLOW_UP("interview-follow-up", "面试追问生成", "prompts/job/interview-follow-up.st");

    private final String code;
    private final String title;
    private final String builtinPath;

    PromptScene(String code, String title, String builtinPath) {
        this.code = code;
        this.title = title;
        this.builtinPath = builtinPath;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getBuiltinPath() {
        return builtinPath;
    }

    /** 按 code 反查场景，未知场景抛 IllegalArgumentException */
    public static PromptScene of(String code) {
        if (code != null) {
            for (PromptScene scene : values()) {
                if (scene.code.equalsIgnoreCase(code.trim())) {
                    return scene;
                }
            }
        }
        throw new IllegalArgumentException("未知的提示词场景:" + code);
    }
}
