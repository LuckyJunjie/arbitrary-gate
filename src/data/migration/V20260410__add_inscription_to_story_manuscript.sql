-- =====================================================
-- Migration: V20260410__add_inscription_to_story_manuscript
-- Desc: 为 story_manuscript 表添加题记字段
-- =====================================================

ALTER TABLE `story_manuscript`
  ADD COLUMN `inscription` VARCHAR(128) DEFAULT NULL COMMENT '题记（散文诗风格，不超过30字）'
  AFTER `baiguan_comment`;
