# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased] - 2026-04-13

### AI 功能
- [x] AI-06: AI 腔词黑名单过滤 — 新增 AiPhraseFilter 工具类 (commit cee92bbf)

### 前端功能
- [x] C-14: 修复AI画师动态导入循环依赖，添加单元测试 (commit 92c5f3ab)
- [x] S-16: 完成断线重连继续阅读功能 (commit 4aed1de7)
- [x] U-06: 个人信息页完善 - 昵称编辑功能 (commit 02ff40be)
- [x] D-04: 关键词卡扩展包支持 (commit 2cafbb7f)

### 前端改进
- [x] UI-09: 缓动曲线全面收敛，所有硬编码 cubic-bezier 替换为 CSS 变量 (commit 9c86273d)

### 后端修复
- [x] fix(backend): 修复 getKeywordCards 和 getEventCardName 硬编码问题 (commit 8b4f57ee)

### 故事功能
- [x] feat(story): 实现故事列表分页查询，支持 page/pageSize 参数 (commit dd366031)

### 工具脚本
- [x] feat(scripts): 批量文档同步工具，支持配置批量入库 (commit d52ac022)

### 测试修复
- [x] fix(tests): 修复 storyStore 和 apiClient 单元测试 (commit feb9619b)

### 文档
- [x] docs: 更新 MEMORY.md 和 opus 检查结果 — UI-09 全面收敛确认，D-04 关键词卡扩展完成 (commit 44b88e7e)
- [x] docs: 确认 A-01~A-07 音效全部完成，MEMORY.md 更新 (commit caa7b7a8)
- [x] docs: update MEMORY.md — 2026-04-13 状态同步 (commit 4d591775)
- [x] docs: 更新 P-02 状态为已完成 + MEMORY.md 状态同步 (commit aa82f003)

---

## [1.0.0] - 2026-04-12
Initial release
