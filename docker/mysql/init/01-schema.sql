-- ============================================================
-- arbitrary-gate 数据库初始化脚本
-- 此脚本在 MySQL 容器首次启动时自动执行
-- ============================================================


-- 创建 event_card 表 (由 JPA/Hibernate 管理，此处仅作记录)
-- JPA 会自动创建此表 (Entity: EventCard)

-- 创建 keyword_card 表 (由 JPA/Hibernate 管理，此处仅作记录)
-- JPA 会自动创建此表 (Entity: KeywordCard)

-- 充值订单表 (需要手动创建，因为不含 Entity)
CREATE TABLE IF NOT EXISTS `pay_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（唯一）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `package_id` VARCHAR(32) NOT NULL COMMENT '套餐ID',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额（元）',
    `ink_stone_count` INT NOT NULL COMMENT '墨晶数量',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0=待支付 1=已支付 2=已取消 3=已退款',
    `wx_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易单号',
    `paid_at` DATETIME DEFAULT NULL COMMENT '支付完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='墨晶充值订单表';
