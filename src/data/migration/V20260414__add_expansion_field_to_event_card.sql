-- ===================================================
-- Migration: D-04 卡池数据分包扩展机制
-- 为 event_card 表添加 expansion 字段
-- ===================================================

-- 添加 expansion 字段（所有现有卡牌默认属于 core 扩展包）
ALTER TABLE `event_card`
    ADD COLUMN `expansion` VARCHAR(32) NOT NULL DEFAULT 'core'
    COMMENT '所属扩展包：core=核心卡池，legend=传说扩展，wuxia=武侠扩展'
    AFTER `era`;

-- 为 expansion 字段添加索引（提升按扩展包查询的性能）
CREATE INDEX `idx_event_card_expansion` ON `event_card` (`expansion`);
