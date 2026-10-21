package com.nuist.agent.zhangxu.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板实体（成员 A：Prompt 模板管理）。
 * 对应表 t_llm_prompt_template：数据库模板优先于 classpath 内置模板，便于运营调优。
 */
@Data
public class LlmPromptTemplate {

    /** 主键 */
    private Long id;

    /** 场景（PromptScene.code） */
    private String scene;

    /** 模板名称 */
    private String name;

    /** 模板内容（{变量} 占位） */
    private String content;

    /** 模板说明 */
    private String description;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
