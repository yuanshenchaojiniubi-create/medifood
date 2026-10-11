-- ============================================================
-- 数据库迁移脚本：补充缺失的表和字段
-- 日期：2026-07-22
-- 说明：同步前几次对话中产生的数据库变更
-- ============================================================

USE `medifood_circle`;

-- ============================================================
-- 1. 添加 suitable_seasons 列到 recipe 表（安全添加，可重复执行）
-- 用于节气/季节精准推荐
-- ============================================================
-- 检查列是否存在，不存在则添加
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'medifood_circle'
    AND TABLE_NAME = 'recipe'
    AND COLUMN_NAME = 'suitable_seasons');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `recipe` ADD COLUMN `suitable_seasons` VARCHAR(500) DEFAULT NULL COMMENT ''适合的节气/季节（逗号分隔，如 立春,春分,春季）'' AFTER `suitable_for`',
    'SELECT ''[INFO] Column suitable_seasons already exists, skipping...'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 创建 private_message 表（私信表）
-- 用于用户之间的私聊功能
-- ============================================================
CREATE TABLE IF NOT EXISTS `private_message` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `sender_id` BIGINT(20) NOT NULL COMMENT '发送者ID',
    `receiver_id` BIGINT(20) NOT NULL COMMENT '接收者ID',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `message_type` VARCHAR(20) DEFAULT 'TEXT' COMMENT '消息类型：TEXT-文本, IMAGE-图片, VOICE-语音',
    `image_url` VARCHAR(500) DEFAULT NULL COMMENT '图片URL',
    `is_read` INT(11) DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    `is_recalled` INT(11) DEFAULT 0 COMMENT '是否撤回：0-否，1-是',
    `quote_message_id` BIGINT(20) DEFAULT NULL COMMENT '引用消息ID',
    `quote_content` VARCHAR(500) DEFAULT NULL COMMENT '引用消息内容',
    `quote_sender_id` BIGINT(20) DEFAULT NULL COMMENT '引用消息发送者ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_sender_receiver` (`sender_id`, `receiver_id`),
    KEY `idx_receiver_read` (`receiver_id`, `is_read`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信表';

-- ============================================================
-- 3. 创建 banner 表（首页轮播图表）
-- 用于管理首页轮播图
-- ============================================================
CREATE TABLE IF NOT EXISTS `banner` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '轮播图ID',
    `image_url` VARCHAR(500) NOT NULL COMMENT '图片URL',
    `title` VARCHAR(200) DEFAULT NULL COMMENT '标题',
    `link_url` VARCHAR(500) DEFAULT NULL COMMENT '跳转链接',
    `sort_order` INT(11) DEFAULT 0 COMMENT '排序顺序（越小越靠前）',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播图表';

-- ============================================================
-- 4. 初始化 banner 数据（示例轮播图，使用 INSERT IGNORE 避免重复）
-- ============================================================
INSERT IGNORE INTO `banner` (`image_url`, `title`, `link_url`, `sort_order`, `status`) VALUES
('https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=1200&h=400&fit=crop', '药食同源，健康养生', '/knowledge', 1, 1),
('https://images.unsplash.com/photo-1540420773420-3366772f4999?w=1200&h=400&fit=crop', '精选健康食谱推荐', '/recipe', 2, 1),
('https://images.unsplash.com/photo-1505576399279-565b52d4ac71?w=1200&h=400&fit=crop', '社区交流，分享心得', '/community', 3, 1);