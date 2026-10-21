package com.nuist.agent.job.ai;

import com.nuist.agent.entity.LlmPromptTemplate;
import com.nuist.agent.mapper.LlmPromptTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板服务（成员 A：Prompt 模板统一管理）。
 *
 * <p>加载顺序：数据库启用中的模板 → classpath:prompts/job/*.st 内置模板 → 兜底短语。
 * 模板按场景缓存，管理员在后台修改后调用 {@link #evictAll()} 生效。</p>
 */
@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);

    private final LlmPromptTemplateMapper promptTemplateMapper;
    private final PromptTemplateRenderer renderer;
    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<PromptScene, TemplateInfo> cache = new ConcurrentHashMap<>();

    public PromptTemplateService(LlmPromptTemplateMapper promptTemplateMapper,
                                 PromptTemplateRenderer renderer,
                                 ResourceLoader resourceLoader) {
        this.promptTemplateMapper = promptTemplateMapper;
        this.renderer = renderer;
        this.resourceLoader = resourceLoader;
    }

    /** 取渲染后的系统提示词 */
    public String resolve(PromptScene scene, Map<String, Object> variables) {
        return renderer.render(template(scene).content(), variables);
    }

    /** 取模板内容与来源（DB / BUILTIN / FALLBACK） */
    public TemplateInfo template(PromptScene scene) {
        return cache.computeIfAbsent(scene, this::load);
    }

    /** 模板来源 */
    public String source(PromptScene scene) {
        return template(scene).source();
    }

    /** 模板中出现的占位符名称（后台模板管理页提示用） */
    public java.util.Set<String> placeholders(PromptScene scene) {
        return renderer.placeholders(template(scene).content());
    }

    private TemplateInfo load(PromptScene scene) {
        if (promptTemplateMapper != null) {
            try {
                LlmPromptTemplate dbTemplate = promptTemplateMapper.selectEnabledByScene(scene.getCode());
                if (dbTemplate != null && StringUtils.hasText(dbTemplate.getContent())) {
                    return new TemplateInfo(dbTemplate.getContent(), "DB");
                }
            } catch (Exception e) {
                // 数据库模板读取失败不影响主流程，退化为内置模板
                log.warn("读取数据库提示词模板失败,scene={},err={}", scene.getCode(), e.getMessage());
            }
        }
        String builtin = loadResource(scene);
        if (builtin != null) {
            return new TemplateInfo(builtin, "BUILTIN");
        }
        return new TemplateInfo(fallbackTemplate(scene), "FALLBACK");
    }

    /** 读取 classpath 内置模板；资源不存在返回 null */
    public String readBuiltin(PromptScene scene) {
        String content = loadResource(scene);
        return content != null ? content : fallbackTemplate(scene);
    }

    private String loadResource(PromptScene scene) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + scene.getBuiltinPath());
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("读取内置提示词模板失败,scene={},err={}", scene.getCode(), e.getMessage());
        }
        return null;
    }

    private String fallbackTemplate(PromptScene scene) {
        return "你是求职辅导助手，请完成【" + scene.getTitle() + "】任务，"
                + "结论要具体、可执行，不要输出与任务无关的内容。";
    }

    /** 清理全部模板缓存（后台修改模板后调用） */
    public void evictAll() {
        cache.clear();
    }

    /** 清理指定场景缓存 */
    public void evict(PromptScene scene) {
        cache.remove(scene);
    }

    /** 模板内容与来源 */
    public record TemplateInfo(String content, String source) {
    }
}
