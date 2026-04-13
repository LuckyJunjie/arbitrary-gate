-- ===================================================
-- 关键词卡池扩展包扩展
-- D-04 卡池数据分包扩展机制 - 关键词卡扩展
-- ===================================================

-- 添加 expansion_code 字段到 keyword_card 表
ALTER TABLE `keyword_card` 
ADD COLUMN `expansion_code` VARCHAR(32) NOT NULL DEFAULT 'core' COMMENT '扩展包代码' AFTER `weight`;

-- 创建关键词卡扩展包定义
INSERT INTO `card_expansion` (`expansion_code`, `expansion_name`, `description`, `card_count`, `enabled`, `sort_order`) VALUES
('kw_core', '核心词池', '游戏初始关键词卡池，1000张涵盖器物/职人/风物/情绪/称谓', 0, 1, 10),
('kw_classic', '经典词库', '收录诗词典故、历史典籍中的经典意象', 0, 1, 11),
('kw_poetic', '诗意词扩展', '收录诗词意境、文学意象的深度挖掘', 0, 1, 12)
ON DUPLICATE KEY UPDATE `expansion_name` = VALUES(`expansion_name`);

-- 更新关键词卡扩展包的 card_count
UPDATE `card_expansion` SET `card_count` = (
    SELECT COUNT(*) FROM `keyword_card` WHERE `expansion_code` = `expansion_code`
) WHERE `expansion_code` LIKE 'kw_%';

-- 将现有 keyword_card 数据标记为 core 扩展包 (已有 DEFAULT 'core')
-- 现有 1000 张关键词卡全部属于核心词池
