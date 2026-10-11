CREATE TABLE IF NOT EXISTS `private_message` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `sender_id`        BIGINT NOT NULL COMMENT '发送者ID',
    `receiver_id`      BIGINT NOT NULL COMMENT '接收者ID',
    `content`          TEXT NOT NULL COMMENT '消息内容',
    `message_type`     VARCHAR(10) DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/VOICE/SHARE',
    `image_url`        VARCHAR(500) COMMENT '图片URL',
    `is_read`          TINYINT DEFAULT 0 COMMENT '0未读/1已读',
    `is_recalled`      TINYINT DEFAULT 0 COMMENT '0未撤回/1已撤回',
    `quote_message_id` BIGINT NULL COMMENT '引用的消息ID',
    `quote_content`    VARCHAR(200) NULL COMMENT '引用消息内容预览',
    `quote_sender_id`  BIGINT NULL COMMENT '引用消息的发送者ID',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX `idx_conversation` (`sender_id`, `receiver_id`, `created_at`),
    INDEX `idx_receiver_unread` (`receiver_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信消息表';