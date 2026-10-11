package com.medifood.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_category")
public class QaCategory {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String categoryName;
    
    private Long parentId;
    
    private Integer sortOrder;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

