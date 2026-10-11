package com.medifood.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_post")
public class CommunityPost {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String content;
    
    private String images;
    
    private Integer postType;
    
    private Integer likeCount;
    
    private Integer commentCount;
    
    private Integer shareCount;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

