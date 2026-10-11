-- ============================================================
-- 内容审核功能迁移脚本（可重复执行）
-- 功能：三级漏斗式内容审核（敏感词拦截 → AI 异步审核 → 管理员人工复核）
-- 说明：新建 content_audit_record 审核台账表；
--       recipe/knowledge 表新增 status=3（审核中）状态值，
--       无需修改列定义（TINYINT 已可容纳），仅更新注释。
-- ============================================================

-- 内容审核台账表
CREATE TABLE IF NOT EXISTS `content_audit_record` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `content_type` TINYINT(4) NOT NULL COMMENT '内容类型：1-社区动态 2-食谱 3-知识文章 4-社区评论 5-食谱评论 6-知识评论 7-提问 8-回答',
    `content_id` BIGINT(20) NOT NULL COMMENT '内容ID（对应各内容表主键）',
    `user_id` BIGINT(20) NOT NULL COMMENT '发布者用户ID',
    `title` VARCHAR(500) DEFAULT NULL COMMENT '标题快照（食谱/知识/提问）',
    `content_snapshot` TEXT COMMENT '内容快照（管理员复核时直接查看）',
    `image_urls` TEXT COMMENT '图片URL列表快照（JSON数组）',
    `audit_mode` TINYINT(1) NOT NULL COMMENT '审核模式：1-先发后审（动态/评论/问答） 2-先审后发（食谱/知识）',
    `ai_category` VARCHAR(50) DEFAULT NULL COMMENT 'AI判定违规类别',
    `ai_reason` VARCHAR(500) DEFAULT NULL COMMENT 'AI判定原因',
    `status` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '状态：0-待人工审核 1-管理员已通过 2-管理员已拒绝 3-已拒绝并警告',
    `admin_id` BIGINT(20) DEFAULT NULL COMMENT '审核管理员用户ID',
    `admin_remark` VARCHAR(500) DEFAULT NULL COMMENT '管理员处理备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_content` (`content_type`, `content_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容审核台账表';

-- recipe / knowledge 状态注释更新（status 新增 3-审核中，列类型无需变更）
ALTER TABLE `recipe`
    MODIFY COLUMN `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-草稿，1-已发布，2-已删除，3-审核中（先审后发）';

ALTER TABLE `knowledge`
    MODIFY COLUMN `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-草稿，1-已发布，2-已删除，3-审核中（先审后发）';
