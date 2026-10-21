/**
 * 成员 C —— 文字模拟面试与反馈（占位包）。
 *
 * <p>本包由成员 C 负责：围绕目标岗位生成面试题、逐题作答与评分点评、多轮追问、
 * 整场反馈报告与训练记录回顾。当前提交只保留包结构，具体实现待成员 C 补充。</p>
 *
 * <p>调用约定：出题/评分/报告统一走 {@code com.nuist.agent.zhangxu.ai.LlmGateway#chatJsonWithMeta}，
 * 场景取 {@code com.nuist.agent.zhangxu.ai.PromptScene}；AI 不可用时降级到本地题库。</p>
 */
package com.nuist.agent.c.interview;
