-- =====================================================
-- AI 模块数据表迁移脚本（安全可重复执行）
-- 说明：使用 INFORMATION_SCHEMA 检查 + CREATE TABLE IF NOT EXISTS
-- 用途：支持多轮对话持久化、用户反馈收集
-- 用法：在 MySQL 中执行 source migration_add_ai_tables.sql;
-- =====================================================

-- 1. ai_conversation 表：AI 对话会话
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT PRIMARY KEY COMMENT '会话ID（全局唯一，从Redis序列生成）',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    title VARCHAR(100) DEFAULT '新会话' COMMENT '会话标题',
    summary TEXT COMMENT '对话历史摘要（LLM压缩生成）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';

-- 2. ai_message 表：AI 对话消息
CREATE TABLE IF NOT EXISTS ai_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    conversation_id BIGINT NOT NULL COMMENT '所属会话ID',
    role VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    token_count INT DEFAULT 0 COMMENT 'Token消耗量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';

-- 3. ai_feedback 表：AI 回答用户反馈（阶段7预留）
CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '反馈ID',
    message_id BIGINT NOT NULL COMMENT '被评价的消息ID',
    user_id BIGINT NOT NULL COMMENT '反馈用户ID',
    feedback_type VARCHAR(20) NOT NULL COMMENT '反馈类型：positive/negative',
    reason VARCHAR(500) COMMENT '反馈原因（踩时可选填）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_message_id (message_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI回答用户反馈表';

SELECT 'AI 模块数据表迁移完成！' AS result;