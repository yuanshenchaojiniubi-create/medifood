package com.medifood.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识评论实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("knowledge_comment")
public class KnowledgeComment {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long knowledgeId;
    
    private Long userId;
    
    private Long parentId;
    
    private String content;
    
    private Integer likeCount;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

