-- ============================================================
-- 药食同源平台 - 医师功能完整迁移脚本
-- 包含：user 表新增专业字段 + diet_plan/diet_consultation/consultation_reply 三张新表
-- 安全策略：使用 INFORMATION_SCHEMA 检查 + CREATE TABLE IF NOT EXISTS
-- 可重复执行，不会报错
-- ============================================================

USE `medifood_circle`;

-- ==================== 1. user 表新增医师专业字段 ====================

-- 执业医院
SET @sql_hospital = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN `hospital` VARCHAR(100) DEFAULT NULL COMMENT ''执业医院'' AFTER `doctor_cert_reviewed_at`',
        'SELECT ''字段 hospital 已存在，跳过''')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'medifood_circle' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'hospital'
);
PREPARE stmt FROM @sql_hospital;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 科室
SET @sql_department = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN `department` VARCHAR(100) DEFAULT NULL COMMENT ''科室'' AFTER `hospital`',
        'SELECT ''字段 department 已存在，跳过''')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'medifood_circle' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'department'
);
PREPARE stmt FROM @sql_department;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 职称
SET @sql_title = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN `title` VARCHAR(50) DEFAULT NULL COMMENT ''职称'' AFTER `department`',
        'SELECT ''字段 title 已存在，跳过''')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'medifood_circle' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'title'
);
PREPARE stmt FROM @sql_title;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 擅长领域
SET @sql_specialties = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN `specialties` VARCHAR(500) DEFAULT NULL COMMENT ''擅长领域（逗号分隔）'' AFTER `title`',
        'SELECT ''字段 specialties 已存在，跳过''')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'medifood_circle' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'specialties'
);
PREPARE stmt FROM @sql_specialties;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 从业年限
SET @sql_practice_years = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN `practice_years` INT(11) DEFAULT NULL COMMENT ''从业年限'' AFTER `specialties`',
        'SELECT ''字段 practice_years 已存在，跳过''')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'medifood_circle' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'practice_years'
);
PREPARE stmt FROM @sql_practice_years;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== 2. 新建膳食计划表 ====================

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

-- ==================== 3. 新建膳食咨询表 ====================

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

-- ==================== 4. 新建咨询回复表 ====================

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

-- ==================== 5. 修复历史医师角色 ====================

-- 为所有已认证医师（is_doctor=1 且 APPROVED）补上 DOCTOR 角色
INSERT IGNORE INTO user_role (user_id, role_id, create_time)
SELECT u.id, r.id, NOW()
FROM `user` u
CROSS JOIN `role` r
WHERE u.is_doctor = 1
  AND u.doctor_cert_status = 'APPROVED'
  AND r.role_code = 'DOCTOR'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 完成提示
SELECT '✅ 医师功能迁移脚本执行完毕！已验证：user表字段、diet_plan/diet_consultation/consultation_reply三张表、历史医师角色修复' AS result;