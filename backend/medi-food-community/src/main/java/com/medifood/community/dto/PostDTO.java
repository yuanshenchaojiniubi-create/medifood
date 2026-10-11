package com.medifood.community.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostDTO {
    
    private Long id;
    
    private Long userId;
    
    private String username;
    
    private String nickname;
    
    private String avatar;
    
    private String content;
    
    private List<String> images;
    
    private Integer postType;
    
    private Integer likeCount;
    
    private Integer commentCount;
    
    private Integer shareCount;
    
    private Boolean isLiked;
    
    private LocalDateTime createTime;
}

