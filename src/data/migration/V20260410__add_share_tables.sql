-- =====================================================
-- V20260410: 合券机制相关表
-- story_share: 故事分享记录
-- special_card: 合券纪念卡
-- story_reader: 故事读者（分享后获得阅读权限）
-- =====================================================

USE `arbitrary_gate`;

-- ---------------------------------------------------
-- 故事分享表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `story_share`;
CREATE TABLE `story_share` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `share_code`       VARCHAR(20)     NOT NULL COMMENT '分享码（8位）',
  `story_id`         BIGINT UNSIGNED NOT NULL COMMENT '关联故事ID',
  `creator_user_id`  BIGINT UNSIGNED NOT NULL COMMENT '分享创建者用户ID',
  `missing_corner_card_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '缺角卡ID（故事作者持有的关键词卡ID）',
  `card_name`        VARCHAR(50)     DEFAULT NULL COMMENT '缺角卡名称（用于前端显示）',
  `card_category`    TINYINT UNSIGNED DEFAULT NULL COMMENT '缺角卡分类',
  `status`           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态: 1=待合券, 2=已合券, 3=已过期',
  `joint_user_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '合券者用户ID',
  `jointed_at`       DATETIME        DEFAULT NULL COMMENT '合券时间',
  `expires_at`       DATETIME        NOT NULL COMMENT '过期时间（7天后）',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_code` (`share_code`),
  KEY `idx_story_id` (`story_id`),
  KEY `idx_creator_user_id` (`creator_user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故事分享表';

-- ---------------------------------------------------
-- 合券纪念卡表（特殊卡牌定义）
-- ---------------------------------------------------
DROP TABLE IF EXISTS `special_card`;
CREATE TABLE `special_card` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `card_no`          VARCHAR(20)     NOT NULL COMMENT '纪念卡编号 SC001',
  `name`             VARCHAR(50)     NOT NULL COMMENT '卡片名称',
  `description`      VARCHAR(255)    DEFAULT NULL COMMENT '卡片描述',
  `image_url`        VARCHAR(500)    DEFAULT NULL COMMENT '卡片图片URL',
  `source_story_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '来源故事ID',
  `source_share_code` VARCHAR(20)    DEFAULT NULL COMMENT '来源分享码',
  `rarity`           TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '稀有度: 1=凡 2=珍 3=奇 4=绝',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_source_story_id` (`source_story_id`),
  KEY `idx_source_share_code` (`source_share_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合券纪念卡表';

-- ---------------------------------------------------
-- 故事读者表（分享后获得阅读权限）
-- ---------------------------------------------------
DROP TABLE IF EXISTS `story_reader`;
CREATE TABLE `story_reader` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `story_id`     BIGINT UNSIGNED NOT NULL COMMENT '故事ID',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '读者用户ID',
  `source_share_code` VARCHAR(20)  DEFAULT NULL COMMENT '来源分享码',
  `read_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得阅读权限时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_story_user` (`story_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故事读者表';

-- ---------------------------------------------------
-- 用户特殊卡牌持有表（扩展 user_card 或新建表）
-- ---------------------------------------------------
DROP TABLE IF EXISTS `user_special_card`;
CREATE TABLE `user_special_card` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `special_card_id`  BIGINT UNSIGNED NOT NULL COMMENT '特殊卡ID',
  `source_share_code` VARCHAR(20)    DEFAULT NULL COMMENT '来源分享码',
  `acquired_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_special_card` (`user_id`, `special_card_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户特殊卡牌持有表';

-- ---------------------------------------------------
-- 插入默认合券纪念卡模板（会被具体故事分享时克隆）
-- ---------------------------------------------------
INSERT INTO `special_card` (`id`, `card_no`, `name`, `description`, `rarity`) VALUES
(1, 'SC-COUPON-001', '合璧笺', '两半残卡相合，时光重现。此卡记录一段跨越时空的相遇。', 3);
