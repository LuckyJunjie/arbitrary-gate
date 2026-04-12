-- ===================================================
-- 卡池扩展包定义初始化数据
-- D-04 卡池数据分包扩展机制
-- ===================================================

-- 先删除旧数据（如果存在）
DELETE FROM `card_expansion`;

-- 插入基础扩展包定义
INSERT INTO `card_expansion` (`expansion_code`, `expansion_name`, `description`, `card_count`, `enabled`, `sort_order`) VALUES
('core', '核心卡池', '游戏初始卡池，包含先秦至明清的历史事件', 600, 1, 0),
('legend', '传说扩展包', '收录各朝代神话传说与民间故事扩展事件', 0, 1, 1),
('wuxia', '武侠风云包', '收录武侠世界江湖恩怨、门派纷争等扩展事件', 0, 1, 2);

-- 更新 event_card 表的 card_count（实际数量）
UPDATE `card_expansion` SET `card_count` = (
    SELECT COUNT(*) FROM `event_card` WHERE `expansion` = `expansion_code`
) WHERE `expansion_code` = 'core';
