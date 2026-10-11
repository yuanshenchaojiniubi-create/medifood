package com.medifood.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_answer")
public class QaAnswer {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long questionId;
    
    private Long userId;
    
    private String content;
    
    private String images;
    
    private Integer likeCount;
    
    private Integer isBest;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

