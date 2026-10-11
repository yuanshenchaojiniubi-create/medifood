package com.medifood.community.dto;

import lombok.Data;

@Data
public class FollowStatsDTO {

    private Integer followingCount;

    private Integer followersCount;

    private Integer mutualCount;

    private Boolean isFollowing;
}