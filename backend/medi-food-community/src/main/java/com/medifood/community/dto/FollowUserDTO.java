package com.medifood.community.dto;

import lombok.Data;

@Data
public class FollowUserDTO {

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private Boolean isFollowed;
}