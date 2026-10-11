-- 药食同源养生交流平台数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `medifood_circle` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `medifood_circle`;

-- ==================== 用户相关表 ====================

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密后）',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `gender` TINYINT(1) DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
    `birthday` DATE DEFAULT NULL COMMENT '生日',
    `bio` VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
    `is_doctor` TINYINT(1) DEFAULT 0 COMMENT '是否为认证医师：0-否，1-是',
    `doctor_cert_status` VARCHAR(20) DEFAULT 'UNSUBMITTED' COMMENT '医师认证状态：UNSUBMITTED/PENDING/APPROVED/REJECTED',
    `doctor_cert_file` VARCHAR(255) DEFAULT NULL COMMENT '医师认证材料路径',
    `doctor_cert_description` VARCHAR(500) DEFAULT NULL COMMENT '医师认证说明',
    `doctor_cert_reason` VARCHAR(255) DEFAULT NULL COMMENT '医师认证审核说明',
    `doctor_cert_applied_at` DATETIME DEFAULT NULL COMMENT '医师认证申请时间',
    `doctor_cert_reviewed_at` DATETIME DEFAULT NULL COMMENT '医师认证审核时间',
    `hospital` VARCHAR(100) DEFAULT NULL COMMENT '执业医院',
    `department` VARCHAR(100) DEFAULT NULL COMMENT '科室',
    `title` VARCHAR(50) DEFAULT NULL COMMENT '职称',
    `specialties` VARCHAR(500) DEFAULT NULL COMMENT '擅长领域（逗号分隔）',
    `practice_years` INT(11) DEFAULT NULL COMMENT '从业年限',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色代码',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_role` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `role_id` BIGINT(20) NOT NULL COMMENT '角色ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ==================== 养生知识相关表 ====================

-- 知识分类表
CREATE TABLE IF NOT EXISTS `knowledge_category` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT(20) DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
    `sort_order` INT(11) DEFAULT 0 COMMENT '排序顺序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识分类表';

-- 养生知识表
CREATE TABLE IF NOT EXISTS `knowledge` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '知识ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '发布用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content` TEXT NOT NULL COMMENT '内容',
    `category_id` BIGINT(20) DEFAULT NULL COMMENT '分类ID',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图片',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON格式）',
    `view_count` INT(11) DEFAULT 0 COMMENT '浏览次数',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT(11) DEFAULT 0 COMMENT '评论数',
    `collect_count` INT(11) DEFAULT 0 COMMENT '收藏数',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-草稿，1-已发布，2-已删除，3-审核中（先审后发：AI/管理员审核通过后转为已发布）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    FULLTEXT KEY `ft_title_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养生知识表';

-- 知识食材关联表（用于筛选）
CREATE TABLE IF NOT EXISTS `knowledge_ingredient` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `knowledge_id` BIGINT(20) NOT NULL COMMENT '知识ID',
    `ingredient_name` VARCHAR(50) NOT NULL COMMENT '食材名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_id` (`knowledge_id`),
    KEY `idx_ingredient_name` (`ingredient_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识食材关联表';

-- 知识点赞表
CREATE TABLE IF NOT EXISTS `knowledge_like` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `knowledge_id` BIGINT(20) NOT NULL COMMENT '知识ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_user` (`knowledge_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点赞表';

-- 知识评论表
CREATE TABLE IF NOT EXISTS `knowledge_comment` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `knowledge_id` BIGINT(20) NOT NULL COMMENT '知识ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '评论用户ID',
    `parent_id` BIGINT(20) DEFAULT 0 COMMENT '父评论ID，0表示顶级评论',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-已删除，1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_id` (`knowledge_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识评论表';

-- 知识收藏表
CREATE TABLE IF NOT EXISTS `knowledge_collect` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `knowledge_id` BIGINT(20) NOT NULL COMMENT '知识ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_user` (`knowledge_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识收藏表';

-- ==================== 健康食谱相关表 ====================

-- 食谱标签表
CREATE TABLE IF NOT EXISTS `recipe_tag` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `tag_name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `tag_type` VARCHAR(20) NOT NULL COMMENT '标签类型：ingredient-食材，effect-功效，season-季节',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name_type` (`tag_name`, `tag_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱标签表';

-- 健康食谱表
CREATE TABLE IF NOT EXISTS `recipe` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '食谱ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '发布用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '食谱名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '食谱描述',
    `ingredients` TEXT NOT NULL COMMENT '食材组成（JSON格式）',
    `steps` TEXT NOT NULL COMMENT '制作步骤（JSON格式）',
    `suitable_for` VARCHAR(500) DEFAULT NULL COMMENT '适用人群',
    `suitable_seasons` VARCHAR(500) DEFAULT NULL COMMENT '适合的节气/季节（逗号分隔，如 立春,春分,春季）用于精准推荐',
    `effects` VARCHAR(500) DEFAULT NULL COMMENT '功效说明',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图片',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON格式）',
    `difficulty` TINYINT(1) DEFAULT 1 COMMENT '难度：1-简单，2-中等，3-困难',
    `cooking_time` INT(11) DEFAULT NULL COMMENT '制作时间（分钟）',
    `servings` INT(11) DEFAULT NULL COMMENT '份量（人数）',
    `view_count` INT(11) DEFAULT 0 COMMENT '浏览次数',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT(11) DEFAULT 0 COMMENT '评论数',
    `collect_count` INT(11) DEFAULT 0 COMMENT '收藏数',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-草稿，1-已发布，2-已删除，3-审核中（先审后发：AI/管理员审核通过后转为已发布）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    FULLTEXT KEY `ft_title_description` (`title`, `description`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康食谱表';

-- 食谱标签关联表
CREATE TABLE IF NOT EXISTS `recipe_tag_relation` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `recipe_id` BIGINT(20) NOT NULL COMMENT '食谱ID',
    `tag_id` BIGINT(20) NOT NULL COMMENT '标签ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recipe_tag` (`recipe_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱标签关联表';

-- 食谱点赞表
CREATE TABLE IF NOT EXISTS `recipe_like` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `recipe_id` BIGINT(20) NOT NULL COMMENT '食谱ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recipe_user` (`recipe_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱点赞表';

-- 食谱评论表
CREATE TABLE IF NOT EXISTS `recipe_comment` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `recipe_id` BIGINT(20) NOT NULL COMMENT '食谱ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '评论用户ID',
    `parent_id` BIGINT(20) DEFAULT 0 COMMENT '父评论ID，0表示顶级评论',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-已删除，1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_recipe_id` (`recipe_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱评论表';

-- 食谱收藏表
CREATE TABLE IF NOT EXISTS `recipe_collect` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `recipe_id` BIGINT(20) NOT NULL COMMENT '食谱ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recipe_user` (`recipe_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱收藏表';

-- ==================== 社区互动相关表 ====================

-- 社区动态表
CREATE TABLE IF NOT EXISTS `community_post` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '动态ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '发布用户ID',
    `content` TEXT NOT NULL COMMENT '动态内容',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON格式）',
    `post_type` TINYINT(1) DEFAULT 1 COMMENT '动态类型：1-普通动态，2-健康打卡，3-心得体会',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT(11) DEFAULT 0 COMMENT '评论数',
    `share_count` INT(11) DEFAULT 0 COMMENT '分享数',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-已删除，1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_post_type` (`post_type`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区动态表';

-- 动态点赞表
CREATE TABLE IF NOT EXISTS `post_like` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `post_id` BIGINT(20) NOT NULL COMMENT '动态ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态点赞表';

-- 动态评论表
CREATE TABLE IF NOT EXISTS `post_comment` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `post_id` BIGINT(20) NOT NULL COMMENT '动态ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '评论用户ID',
    `parent_id` BIGINT(20) DEFAULT 0 COMMENT '父评论ID，0表示顶级评论',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-已删除，1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_post_id` (`post_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态评论表';

-- 用户关注表
CREATE TABLE IF NOT EXISTS `user_follow` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `follower_id` BIGINT(20) NOT NULL COMMENT '关注者ID',
    `following_id` BIGINT(20) NOT NULL COMMENT '被关注者ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`),
    KEY `idx_follower_id` (`follower_id`),
    KEY `idx_following_id` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注表';

-- 好友申请表
CREATE TABLE IF NOT EXISTS `friend_request` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    `from_user_id` BIGINT(20) NOT NULL COMMENT '发起申请的用户ID',
    `to_user_id` BIGINT(20) NOT NULL COMMENT '接收申请的用户ID',
    `message` VARCHAR(200) DEFAULT '我想和你成为好友' COMMENT '申请附言',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理, ACCEPTED-已同意, REJECTED-已拒绝',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_from_user` (`from_user_id`),
    KEY `idx_to_user` (`to_user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请表';

-- ==================== 问答交流相关表 ====================

-- 问题分类表
CREATE TABLE IF NOT EXISTS `qa_category` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT(20) DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
    `sort_order` INT(11) DEFAULT 0 COMMENT '排序顺序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题分类表';

-- 问答问题表
CREATE TABLE IF NOT EXISTS `qa_question` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '问题ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '提问用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '问题标题',
    `content` TEXT NOT NULL COMMENT '问题内容',
    `category_id` BIGINT(20) DEFAULT NULL COMMENT '分类ID',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON格式）',
    `view_count` INT(11) DEFAULT 0 COMMENT '浏览次数',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `answer_count` INT(11) DEFAULT 0 COMMENT '回答数',
    `best_answer_id` BIGINT(20) DEFAULT NULL COMMENT '最佳答案ID',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-已删除，1-正常，2-已解决',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    FULLTEXT KEY `ft_title_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答问题表';

-- 问答回答表
CREATE TABLE IF NOT EXISTS `qa_answer` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '回答ID',
    `question_id` BIGINT(20) NOT NULL COMMENT '问题ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '回答用户ID',
    `content` TEXT NOT NULL COMMENT '回答内容',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON格式）',
    `like_count` INT(11) DEFAULT 0 COMMENT '点赞数',
    `is_best` TINYINT(1) DEFAULT 0 COMMENT '是否最佳答案：0-否，1-是',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-已删除，1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_question_id` (`question_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_best` (`is_best`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答回答表';

-- 问题点赞表
CREATE TABLE IF NOT EXISTS `qa_question_like` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `question_id` BIGINT(20) NOT NULL COMMENT '问题ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_user` (`question_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题点赞表';

-- 回答点赞表
CREATE TABLE IF NOT EXISTS `qa_answer_like` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `answer_id` BIGINT(20) NOT NULL COMMENT '回答ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_answer_user` (`answer_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回答点赞表';

-- ==================== 消息通知相关表 ====================

-- 强通知明细表（评论/回复/关注等）
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `recipient_id` BIGINT(20) NOT NULL COMMENT '接收人ID',
    `actor_id` BIGINT(20) NOT NULL COMMENT '触发人ID',
    `type` TINYINT(1) NOT NULL COMMENT '通知类型：1点赞 2评论 3回复 4关注 5收藏',
    `entity_type` TINYINT(1) NOT NULL COMMENT '实体类型：1知识 2食谱 3动态 4问答',
    `entity_id` BIGINT(20) NOT NULL COMMENT '实体ID',
    `extra_id` BIGINT(20) DEFAULT 0 COMMENT '扩展ID（父评论等）',
    `is_read` TINYINT(1) DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_recipient_read_time` (`recipient_id`, `is_read`, `create_time`),
    KEY `idx_entity` (`entity_type`, `entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知明细表';

-- 聚合通知表（点赞/收藏等高频事件聚合）
CREATE TABLE IF NOT EXISTS `notification_aggregate` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '聚合通知ID',
    `recipient_id` BIGINT(20) NOT NULL COMMENT '接收人ID',
    `type` TINYINT(1) NOT NULL COMMENT '聚合类型：1点赞 5收藏',
    `entity_type` TINYINT(1) NOT NULL COMMENT '实体类型：1知识 2食谱 3动态 4问答',
    `entity_id` BIGINT(20) NOT NULL COMMENT '实体ID',
    `unread_count` INT(11) NOT NULL DEFAULT 0 COMMENT '未读计数',
    `total_count` INT(11) NOT NULL DEFAULT 0 COMMENT '累计次数',
    `latest_actor_id` BIGINT(20) DEFAULT NULL COMMENT '最近触发人',
    `actor_sample` VARCHAR(255) DEFAULT NULL COMMENT '触发人样本（逗号分隔）',
    `last_event_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近触发时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recipient_type_entity` (`recipient_id`, `type`, `entity_type`, `entity_id`),
    KEY `idx_recipient_unread_time` (`recipient_id`, `unread_count`, `last_event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聚合通知表';

-- ==================== 初始化数据 ====================

-- 初始化角色数据
INSERT INTO `role` (`role_name`, `role_code`, `description`) VALUES
('普通用户', 'USER', '普通用户角色'),
('专业医师', 'DOCTOR', '专业医师角色'),
('管理员', 'ADMIN', '系统管理员角色');

-- 初始化知识分类数据
INSERT INTO `knowledge_category` (`category_name`, `parent_id`, `sort_order`) VALUES
('食疗养生', 0, 1),
('药膳搭配', 0, 2),
('养生方法', 0, 3),
('食材知识', 0, 4);

-- 初始化问题分类数据
INSERT INTO `qa_category` (`category_name`, `parent_id`, `sort_order`) VALUES
('食疗咨询', 0, 1),
('药膳搭配', 0, 2),
('疾病咨询', 0, 3),
('健康养生', 0, 4);

-- 初始化食谱标签数据（示例）
INSERT INTO `recipe_tag` (`tag_name`, `tag_type`) VALUES
('枸杞', 'ingredient'),
('红枣', 'ingredient'),
('山药', 'ingredient'),
('补气养血', 'effect'),
('滋阴润燥', 'effect'),
('春季', 'season'),
('夏季', 'season'),
('秋季', 'season'),
('冬季', 'season');

-- ==================== 私信与通信相关表 ====================

-- 私信表
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

-- ==================== 运营管理相关表 ====================

-- 首页轮播图表
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

-- 初始化 banner 数据
INSERT INTO `banner` (`image_url`, `title`, `link_url`, `sort_order`, `status`) VALUES
('https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=1200&h=400&fit=crop', '药食同源，健康养生', '/knowledge', 1, 1),
('https://images.unsplash.com/photo-1540420773420-3366772f4999?w=1200&h=400&fit=crop', '精选健康食谱推荐', '/recipe', 2, 1),
('https://images.unsplash.com/photo-1505576399279-565b52d4ac71?w=1200&h=400&fit=crop', '社区交流，分享心得', '/community', 3, 1);

-- ==================== 内容审核相关表 ====================

-- 内容审核台账表（记录 AI 判定疑似违规的内容，供管理员人工复核）
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

-- ==================== 医师服务相关表 ====================

-- 膳食计划表
CREATE TABLE IF NOT EXISTS `diet_plan` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '计划ID',
    `doctor_id` BIGINT(20) NOT NULL COMMENT '医师ID',
    `patient_id` BIGINT(20) NOT NULL COMMENT '患者ID',
    `title` VARCHAR(200) NOT NULL COMMENT '计划标题',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '计划描述',
    `content` TEXT DEFAULT NULL COMMENT '详细膳食计划内容',
    `target_symptoms` VARCHAR(500) DEFAULT NULL COMMENT '针对症状',
    `duration_days` INT(11) DEFAULT NULL COMMENT '建议执行天数',
    `notes` TEXT DEFAULT NULL COMMENT '注意事项',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：0-草稿，1-已发布，2-已归档',
    `view_count` INT(11) DEFAULT 0 COMMENT '浏览次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_doctor_id` (`doctor_id`),
    KEY `idx_patient_id` (`patient_id`),
    KEY `idx_doctor_patient` (`doctor_id`, `patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='膳食计划表';

-- 膳食咨询表
CREATE TABLE IF NOT EXISTS `diet_consultation` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '咨询ID',
    `patient_id` BIGINT(20) NOT NULL COMMENT '患者ID',
    `doctor_id` BIGINT(20) NOT NULL COMMENT '医师ID',
    `title` VARCHAR(200) NOT NULL COMMENT '咨询标题',
    `content` TEXT DEFAULT NULL COMMENT '咨询内容',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON格式）',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待回复，REPLIED-已回复，CLOSED-已关闭',
    `reply_count` INT(11) DEFAULT 0 COMMENT '回复数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_patient_id` (`patient_id`),
    KEY `idx_doctor_id` (`doctor_id`),
    KEY `idx_status` (`status`),
    KEY `idx_doctor_status` (`doctor_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='膳食咨询表';

-- 咨询回复表
CREATE TABLE IF NOT EXISTS `consultation_reply` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '回复ID',
    `consultation_id` BIGINT(20) NOT NULL COMMENT '咨询ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '回复用户ID',
    `content` TEXT NOT NULL COMMENT '回复内容',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON格式）',
    `is_doctor_reply` TINYINT(1) DEFAULT 0 COMMENT '是否医师回复：0-否，1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_consultation_id` (`consultation_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询回复表';

-- =====================================================
-- AI 模块数据表
-- =====================================================

-- AI 对话会话表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id` BIGINT(20) NOT NULL COMMENT '会话ID（全局唯一，从Redis序列生成）',
    `user_id` BIGINT(20) NOT NULL COMMENT '所属用户ID',
    `title` VARCHAR(100) DEFAULT '新会话' COMMENT '会话标题',
    `summary` TEXT COMMENT '对话历史摘要（LLM压缩生成）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';

-- AI 对话消息表
CREATE TABLE IF NOT EXISTS `ai_message` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` BIGINT(20) NOT NULL COMMENT '所属会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant/system',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `token_count` INT DEFAULT 0 COMMENT 'Token消耗量',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';

-- AI 回答用户反馈表
CREATE TABLE IF NOT EXISTS `ai_feedback` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
    `message_id` BIGINT(20) NOT NULL COMMENT '被评价的消息ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '反馈用户ID',
    `feedback_type` VARCHAR(20) NOT NULL COMMENT '反馈类型：positive/negative',
    `reason` VARCHAR(500) COMMENT '反馈原因',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_message_id` (`message_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI回答用户反馈表';

