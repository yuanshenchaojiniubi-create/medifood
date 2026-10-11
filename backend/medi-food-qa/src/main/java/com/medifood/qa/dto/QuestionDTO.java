package com.medifood.qa.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionDTO {
    
    private Long id;
    
    private Long userId;
    
    private String username;
    
    private String nickname;
    
    private String avatar;
    
    private String title;
    
    private String content;
    
    private Long categoryId;
    
    private String categoryName;
    
    private List<String> images;
    
    private Integer viewCount;
    
    private Integer likeCount;
    
    private Integer answerCount;
    
    private Long bestAnswerId;
    
    private Boolean isLiked;
    
    private LocalDateTime createTime;
}

