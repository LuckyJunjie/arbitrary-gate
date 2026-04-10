-- =====================================================
-- Migration: V20260412
-- Description: P-04 添加关键词卡意象标签；S-14 添加故事偶遇表
-- =====================================================

USE `arbitrary_gate`;

-- ---------------------------------------------------
-- 1. P-04: 为 keyword_card 表添加 tag 字段
--    用于三水意象彩蛋检测（水相关词：渡口/船/江/河/雨/水/舟/帆/潮/浪）
-- ---------------------------------------------------
ALTER TABLE `keyword_card`
    ADD COLUMN `tag` VARCHAR(50) DEFAULT NULL COMMENT 'P-04 意象标签，如"水"表示水相关意象' AFTER `imageUrl`;

-- 为现有水相关卡牌设置标签（示例，基于 name 推断）
UPDATE `keyword_card` SET `tag` = '水' WHERE `name` LIKE '%渡口%' OR `name` LIKE '%船%' OR `name` LIKE '%江%' OR `name` LIKE '%河%' OR `name` LIKE '%雨%' OR `name` LIKE '%水%' OR `name` LIKE '%舟%' OR `name` LIKE '%帆%' OR `name` LIKE '%潮%' OR `name` LIKE '%浪%';

-- ---------------------------------------------------
-- 2. S-14: 创建故事偶遇表
--    用于存储章节间随机触发的配角偶遇事件
-- ---------------------------------------------------
DROP TABLE IF EXISTS `story_encounter`;
CREATE TABLE `story_encounter` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `story_id`      INT UNSIGNED NOT NULL COMMENT '所属故事ID',
  `chapter_no`    INT UNSIGNED NOT NULL COMMENT '触发章节号（选择后进入下一章）',
  `encounter_text` VARCHAR(255) NOT NULL COMMENT '偶遇场景描述（50-80字）',
  `option_a`     VARCHAR(100) NOT NULL DEFAULT '搭话' COMMENT '选项A文字',
  `option_b`     VARCHAR(100) NOT NULL DEFAULT '装作没看见' COMMENT '选项B文字',
  `choice_result` VARCHAR(10) DEFAULT NULL COMMENT '用户选择：A/B',
  `fate_change`  INT NOT NULL DEFAULT 0 COMMENT '命运值变化（搭话+10，装没看见-5）',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_story_id` (`story_id`),
  KEY `idx_chapter_no` (`chapter_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故事偶遇表';
