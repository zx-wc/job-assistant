/**
 * 成员 D —— 前端页面与交互（占位包）。
 *
 * <p>前端为原生 ES Module + Hash 路由 SPA（零构建），静态资源位于
 * {@code src/main/resources/static/job/}：登录注册、求职概览、简历管理、岗位管理、
 * 分析与报告、模拟面试与反馈、AI 网关管理共 11 个页面。</p>
 *
 * <p>当前提交只保留包结构说明；页面资源与 {@code api.js} 由成员 D 补充。
 * 前端统一通过 {@code /api/job/**} 访问后端，令牌放在 {@code Authorization: Bearer <token>}。</p>
 */
package com.nuist.agent.d.web;
