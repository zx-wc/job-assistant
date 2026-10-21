package com.nuist.agent.zhangxu.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 敏感词类型主要分为2种 1 deny类型 需要拦截 2 allow 放行
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveWord implements Serializable {
    // 序列化版本号
    private static final long serialVersionUID = 1L;
    // 主键
    private Long id;
    // 敏感词内容
    private String word;
    // 类型
    private String type;
}
