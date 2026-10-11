package com.medifood.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_answer_like")
public class QaAnswerLike {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long answerId;
    
    private Long userId;
    
    private LocalDateTime createTime;
}

