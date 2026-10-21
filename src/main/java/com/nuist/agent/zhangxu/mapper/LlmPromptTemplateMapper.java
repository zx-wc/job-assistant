package com.nuist.agent.zhangxu.mapper;

import com.nuist.agent.zhangxu.entity.LlmPromptTemplate;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 提示词模板 Mapper（成员 A：Prompt 模板管理）。
 * 表结构见 sql/job_portal.sql —— t_llm_prompt_template。
 * 未在库中配置场景时，网关切回 classpath:prompts/job/*.st 内置模板。
 */
public interface LlmPromptTemplateMapper {

    /** 查询全部模板 */
    @Select("""
            select id, scene, name, content, description, enabled, create_time, update_time
            from t_llm_prompt_template
            order by scene asc
            """)
    List<LlmPromptTemplate> selectAll();

    /** 按场景查询启用中的模板 */
    @Select("""
            select id, scene, name, content, description, enabled, create_time, update_time
            from t_llm_prompt_template
            where scene = #{scene} and enabled = 1
            """)
    LlmPromptTemplate selectEnabledByScene(@Param("scene") String scene);

    /** 按场景查询模板（含停用） */
    @Select("""
            select id, scene, name, content, description, enabled, create_time, update_time
            from t_llm_prompt_template
            where scene = #{scene}
            """)
    LlmPromptTemplate selectByScene(@Param("scene") String scene);

    /** 新增或更新模板（scene 唯一键） */
    @Insert("""
            insert into t_llm_prompt_template(scene, name, content, description, enabled)
            values (#{scene}, #{name}, #{content}, #{description}, #{enabled})
            on duplicate key update
                name = values(name),
                content = values(content),
                description = values(description),
                enabled = values(enabled),
                update_time = now()
            """)
    int upsert(LlmPromptTemplate template);

    /** 删除模板（回到内置模板） */
    @Delete("delete from t_llm_prompt_template where scene = #{scene}")
    int deleteByScene(@Param("scene") String scene);
}
