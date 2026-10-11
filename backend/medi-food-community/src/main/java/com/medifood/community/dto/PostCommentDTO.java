package com.medifood.community.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostCommentDTO {
    private Long id;
    private Long postId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private Long parentId;
    private String content;
    private Integer likeCount;
    private Boolean isLiked;
    private List<PostCommentDTO> replies;
    private LocalDateTime createTime;
}
