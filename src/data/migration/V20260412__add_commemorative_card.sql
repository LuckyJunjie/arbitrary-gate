-- =====================================================
-- V20260412: 合券纪念卡表 (story_commemorative_card)
-- 记录每次合券生成的限定纪念卡
-- =====================================================

USE `arbitrary_gate`;

-- ---------------------------------------------------
-- 合券纪念卡表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `story_commemorative_card`;
CREATE TABLE `story_commemorative_card` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `card_no`             VARCHAR(30)     NOT NULL COMMENT '纪念卡编号 CM-YYYYMMDD-XXXXXX',
  `story_id`            BIGINT UNSIGNED NOT NULL COMMENT '关联故事ID',
  `share_code`          VARCHAR(20)     NOT NULL COMMENT '来源分享码',
  `story_title`         VARCHAR(100)   NOT NULL COMMENT '故事标题',
  `user1_open_id`       VARCHAR(64)     NOT NULL COMMENT '用户1 OpenID（分享者）',
  `user1_nickname`      VARCHAR(50)     NOT NULL COMMENT '用户1昵称',
  `user2_open_id`       VARCHAR(64)     NOT NULL COMMENT '用户2 OpenID（合券者）',
  `user2_nickname`      VARCHAR(50)     NOT NULL COMMENT '用户2昵称',
  `ending_type`         VARCHAR(30)     NOT NULL COMMENT '结局类型',
  `exclusive_mark`      VARCHAR(10)     NOT NULL COMMENT '专属印记（四字吉语）',
  `joint_time`          DATETIME        NOT NULL COMMENT '合券时间',
  `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_story_id` (`story_id`),
  KEY `idx_share_code` (`share_code`),
  KEY `idx_user1_open_id` (`user1_open_id`),
  KEY `idx_user2_open_id` (`user2_open_id`),
  KEY `idx_ending_type` (`ending_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合券纪念卡表';
