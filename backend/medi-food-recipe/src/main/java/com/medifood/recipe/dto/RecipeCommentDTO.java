package com.medifood.recipe.dto;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识评论 DTO
 *
 * @author MediFood Team
 */
@Data
public class RecipeCommentDTO {

    private Long id;

    private Long recipeId;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private Long parentId;

    private String content;

    private Integer likeCount;

    private Boolean isLiked;

    private List<RecipeCommentDTO> replies;

    private LocalDateTime createTime;
}


