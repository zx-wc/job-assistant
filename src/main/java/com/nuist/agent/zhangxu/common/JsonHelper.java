package com.nuist.agent.zhangxu.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 序列化助手（成员 A/B/C 共用）。
 *
 * <p>用于把大模型返回的结构化列表/评分对象存入 MySQL 的 TEXT 字段，以及读取时反序列化；
 * 任何解析失败都退化为空集合，绝不让脏数据导致接口 500。</p>
 */
@Component
public class JsonHelper {

    private static final Logger log = LoggerFactory.getLogger(JsonHelper.class);

    private final ObjectMapper objectMapper;

    public JsonHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 序列化，失败返回 "[]"（列表场景）或 "{}" */
    public String toJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败:{}", e.getMessage());
            return "[]";
        }
    }

    /** 读取字符串列表 */
    public List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("JSON 反序列化字符串列表失败:{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 读取 Map&lt;String, Integer&gt;（维度评分） */
    public Map<String, Integer> toIntMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Map<String, Integer> result = new LinkedHashMap<>();
            if (raw != null) {
                raw.forEach((key, value) -> result.put(key, toInt(value)));
            }
            return result;
        } catch (Exception e) {
            log.warn("JSON 反序列化评分对象失败:{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 任意对象转 int（容错：字符串数字、小数、null） */
    public int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value).trim()));
        } catch (Exception e) {
            return 0;
        }
    }
}
