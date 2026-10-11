package com.medifood.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识食材关联实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("knowledge_ingredient")
public class KnowledgeIngredient {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long knowledgeId;
    
    private String ingredientName;
    
    private LocalDateTime createTime;
}

