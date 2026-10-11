package com.medifood.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识分类实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("knowledge_category")
public class KnowledgeCategory {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String categoryName;
    
    private Long parentId;
    
    private Integer sortOrder;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

