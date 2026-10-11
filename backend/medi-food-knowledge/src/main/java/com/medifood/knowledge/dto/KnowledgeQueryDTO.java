package com.medifood.knowledge.dto;

import lombok.Data;

/**
 * 知识查询 DTO
 * 
 * @author MediFood Team
 */
@Data
public class KnowledgeQueryDTO {
    
    /**
     * 页码
     */
    private Integer page ;
    
    /**
     * 每页大小
     */
    private Integer size ;
    
    /**
     * 分类ID
     */
    private Long categoryId;
    
    /**
     * 食材名称
     */
    private String ingredient;
    
    /**
     * 关键词（搜索标题和内容）
     */
    private String keyword;
    
    /**
     * 排序方式：latest-最新，hot-最热
     */
    private String sort = "latest";
}

