# Changelog - 时光笺 · 任意门

所有重要变更都应记录在此文件。

## [Unreleased]

### AI 功能
- [x] AI-06: AI 腔词黑名单过滤 — 新增 AiPhraseFilter 工具类，过滤 StorytellerAgent 场景描写和手稿、BaiguanAgent 后日谈和总评中的 60+ 个高频 AI 腔词（宛如、仿佛、无法言说、此时此刻等）

## [1.0.0] - 2026-04-10

### P0 - MVP 核心功能 ✅
- [x] 墨池抽卡（基础动画 + 抽卡逻辑）
- [x] 卡匣展示（卡片墙 + 分类筛选）
- [x] 故事阅读界面（卷轴竖排 + 基本翻页）
- [x] AI 接入（说书人 + 判官 Agent）
- [x] 故事生成（完整生命周期）

### P1 - 核心体验 ✅
- [x] 入局三问定制
- [x] 手势选择交互
- [x] 关键词共鸣可视化
- [x] 涟漪波纹动画
- [x] 手稿生成（含朱批）
- [x] 书架管理
- [x] AI Prompt 热更新基础设施
- [x] 文学风格输出差异

### P2 - 社交传播 ✅
- [x] 分享码 + 缺角故事卡
- [x] 合券机制
- [x] AI 画师（卡面图生成）
- [x] 音效系统
- [x] 组合判词生成

### P3 - 体验增强 ✅
- [x] 墨香标记渐淡动画
- [x] 组合预览判词
- [x] AI 画师（完整实现）
- [x] 稀有组合成就
- [x] 用户交互优化

### Bug 修复
- [x] aiPainter.ts 模板字符串语法错误修复
- [x] 墨迹占卜 data-testid 修复
- [x] api.ts 导出修复

### 测试
- [x] 243 个单元测试全部通过
- [x] E2E 测试覆盖：pool, story, manuscript, bookshelf, entry-questions, ai-painter

---

## 技术栈

- **前端**: Vue3 + TypeScript + Vite + Pinia + Vue Router
- **AI**: 通义千问 / 文心一言（文本生成）+ 通义万相（图片生成）
- **部署**: Vercel

## Git History (Recent)

| Commit | Description |
|--------|-------------|
| a5ff01b | feat(C-11): 墨香渐淡每日零点定时衰减任务 |
| 49d36c8 | feat(C-09/E-07/UI-08): 残片拼接擦墨动画 + 关键词落位可视化 |
| c766c6e | feat: 完成 S-14/C-12/P-04/M-10 剩余功能 |
| b9e7a64 | feat(P-02): 实现稀有组合成就检测逻辑 |
| 20f50cd | fix(aiPainter): 修复 buildPlaceholderSvg 模板字符串语法错误 |
