-- migration: V20260409__add_first_impression_to_story_character.sql
-- 描述: 为 story_character 表添加初见印象字段（E-06 配角初见·一句话印象）

-- 如果 story_character 表不存在（某些环境使用 Java 实体而非 DB 表），
-- 此迁移为 no-op；实体字段已添加至 StoryCharacter.java
-- 如使用 JPA/MyBatis-Plus 自动建表，需确保实体字段映射正确

-- 已有表结构的项目执行以下 ALTER（根据实际表名调整）
-- ALTER TABLE `story_character` ADD COLUMN `first_impression` VARCHAR(128) DEFAULT NULL COMMENT '初见印象（一句话，不超过30字）' AFTER `relation_to_user`;
