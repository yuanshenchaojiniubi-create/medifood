package com.medifood.qa.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnswerDTO {
    
    private Long id;
    
    private Long questionId;
    
    private Long userId;
    
    private String username;
    
    private String nickname;
    
    private String avatar;
    
    private String content;
    
    private List<String> images;
    
    private Integer likeCount;
    
    private Boolean isBest;
    
    private Boolean isLiked;
    
    private LocalDateTime createTime;
}

