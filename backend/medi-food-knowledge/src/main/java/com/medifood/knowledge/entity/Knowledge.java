package com.medifood.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 养生知识实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("knowledge")
public class Knowledge {
    
    /**
     * 知识ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 发布用户ID
     */
    private Long userId;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 内容
     */
    private String content;
    
    /**
     * 分类ID
     */
    private Long categoryId;
    
    /**
     * 封面图片
     */
    private String coverImage;
    
    /**
     * 图片列表（JSON格式）
     */
    private String images;
    
    /**
     * 浏览次数
     */
    private Integer viewCount;
    
    /**
     * 点赞数
     */
    private Integer likeCount;
    
    /**
     * 评论数
     */
    private Integer commentCount;
    
    /**
     * 收藏数
     */
    private Integer collectCount;
    
    /**
     * 状态：0-草稿，1-已发布，2-已删除
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

