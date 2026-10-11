package com.medifood.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.community.dto.FollowStatsDTO;
import com.medifood.community.dto.FollowUserDTO;
import com.medifood.community.dto.PostDTO;
import com.medifood.community.dto.PostPublishDTO;

public interface CommunityService {
    
    Long publishPost(Long userId, PostPublishDTO publishDTO);
    
    Page<PostDTO> getFeed(Long userId, Integer page, Integer size, boolean fallbackToHot);
    
    Page<PostDTO> getHotPosts(Integer page, Integer size, Long currentUserId);
    
    void likePost(Long postId, Long userId);
    
    void unlikePost(Long postId, Long userId);
    
    void commentPost(Long userId, Long postId, String content, Long parentId);
    
    void followUser(Long followerId, Long followingId);
    
    void unfollowUser(Long followerId, Long followingId);

    FollowStatsDTO getFollowStats(Long userId, Long currentUserId);

    Page<FollowUserDTO> getFollowers(Long userId, Integer page, Integer size, Long currentUserId);

    Page<FollowUserDTO> getFollowing(Long userId, Integer page, Integer size, Long currentUserId);

    Page<FollowUserDTO> getMutualFollows(Long userId, Integer page, Integer size, Long currentUserId);

    void deletePost(Long postId, Long userId);

    /**
     * 获取动态详情
     */
    PostDTO getPostById(Long id, Long currentUserId);

    Page<PostDTO> searchPosts(String keyword, Integer page, Integer size, Long currentUserId);

    /**
     * 获取用户发布的动态
     */
    Page<PostDTO> getUserPosts(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取用户点赞的动态
     */
    Page<PostDTO> getUserLikedPosts(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取用户收藏的动态
     */
    Page<PostDTO> getUserCollectedPosts(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取动态评论（仅顶层，含回复）
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.medifood.community.dto.PostCommentDTO> getPostComments(Long postId, Integer page, Integer size, Long currentUserId);
}

