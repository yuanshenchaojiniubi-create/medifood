package com.medifood.community.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.Result;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifood.community.dto.FollowStatsDTO;
import com.medifood.community.dto.FollowUserDTO;
import com.medifood.community.dto.PostDTO;
import com.medifood.community.dto.PostPublishDTO;
import com.medifood.community.dto.UserInfoDTO;
import com.medifood.community.entity.*;
import com.medifood.common.audit.ContentAuditEvent;
import com.medifood.common.audit.ContentAuditEventPublisher;
import com.medifood.common.util.SensitiveWordFilter;
import com.medifood.community.feign.UserClient;
import com.medifood.community.mapper.*;
import com.medifood.community.service.CommunityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommunityServiceImpl implements CommunityService {
    
    @Autowired
    private CommunityPostMapper postMapper;
    
    @Autowired
    private PostLikeMapper likeMapper;
    
    @Autowired
    private PostCommentMapper commentMapper;
    
    @Autowired
    private UserFollowMapper followMapper;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private UserClient userClient;

    @Autowired
    private ContentAuditEventPublisher auditEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishPost(Long userId, PostPublishDTO publishDTO) {
        // 第一层防线：敏感词同步拦截（命中直接拒绝发布）
        SensitiveWordFilter.check(publishDTO.getContent(), "动态内容");

        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setContent(publishDTO.getContent());
        post.setPostType(publishDTO.getPostType());
        
        if (publishDTO.getImages() != null && !publishDTO.getImages().isEmpty()) {
            post.setImages(JSONUtil.toJsonStr(publishDTO.getImages()));
        }
        
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setShareCount(0);
        post.setStatus(1);
        
        postMapper.insert(post);

        // 同步到 Elasticsearch
        try {
            java.util.Map<String, Object> source = new java.util.HashMap<>();
            source.put("id", post.getId().toString());
            source.put("content", post.getContent());
            source.put("userId", post.getUserId().toString());
            elasticsearchUtil.indexDocument("community_post_index", post.getId().toString(), source);
        } catch (Exception e) {
            log.error("同步动态到ES失败，动态ID: {}", post.getId(), e);
        }

        log.info("发布动态成功，动态ID: {}, 用户ID: {}", post.getId(), userId);

        // 第二层防线：AI 异步审核（先发后审，判定疑似/违规时自动处理并转人工复核）
        auditEventPublisher.publish(new ContentAuditEvent(
                post.getId(), post.getUserId(), ContentAuditEvent.TYPE_POST,
                null, post.getContent(), publishDTO.getImages()));

        return post.getId();
    }
    
    @Override
    public Page<PostDTO> getFeed(Long userId, Integer page, Integer size, boolean fallbackToHot) {
        List<Long> followingIds = followMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
        ).stream().map(UserFollow::getFollowingId).collect(Collectors.toList());

        if (followingIds.isEmpty()) {
            if (fallbackToHot) {
                return getHotPosts(page, size, userId);
            }
            return new Page<>(page, size);
        }

        Page<CommunityPost> postPage = new Page<>(page, size);
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CommunityPost::getUserId, followingIds)
                .eq(CommunityPost::getStatus, 1)
                .orderByDesc(CommunityPost::getCreateTime);

        Page<CommunityPost> result = postMapper.selectPage(postPage, wrapper);
        return convertToDTOPage(result, userId);
    }
    
    @Override
    public Page<PostDTO> getHotPosts(Integer page, Integer size, Long currentUserId) {
        Page<CommunityPost> postPage = new Page<>(page, size);
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityPost::getStatus, 1)
                .orderByDesc(CommunityPost::getLikeCount)
                .orderByDesc(CommunityPost::getCommentCount);
        
        Page<CommunityPost> result = postMapper.selectPage(postPage, wrapper);
        return convertToDTOPage(result, currentUserId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likePost(Long postId, Long userId) {
        LambdaQueryWrapper<PostLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId);
        
        if (likeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经点赞过了");
        }
        
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        likeMapper.insert(like);
        
        postMapper.incrementLikeCount(postId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikePost(Long postId, Long userId) {
        LambdaQueryWrapper<PostLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId);
        
        PostLike like = likeMapper.selectOne(wrapper);
        if (like == null) {
            throw new BusinessException("还未点赞");
        }
        
        likeMapper.deleteById(like.getId());
        postMapper.decrementLikeCount(postId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commentPost(Long userId, Long postId, String content, Long parentId) {
        // 第一层防线：敏感词同步拦截
        SensitiveWordFilter.check(content, "评论内容");

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId != null ? parentId : 0L);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setStatus(1);

        commentMapper.insert(comment);
        postMapper.incrementCommentCount(postId);

        // 第二层防线：AI 异步审核（先发后审）
        auditEventPublisher.publish(new ContentAuditEvent(
                comment.getId(), userId, ContentAuditEvent.TYPE_POST_COMMENT,
                null, content, null));
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException("不能关注自己");
        }
        
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId);
        
        if (followMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经关注过了");
        }
        
        UserFollow follow = new UserFollow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        followMapper.insert(follow);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollowUser(Long followerId, Long followingId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId);
        
        UserFollow follow = followMapper.selectOne(wrapper);
        if (follow == null) {
            throw new BusinessException("还未关注");
        }
        
        followMapper.deleteById(follow.getId());
    }

    @Override
    public FollowStatsDTO getFollowStats(Long userId, Long currentUserId) {
        FollowStatsDTO stats = new FollowStatsDTO();

        Long followingCount = followMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId));
        stats.setFollowingCount(followingCount != null ? followingCount.intValue() : 0);

        Long followersCount = followMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowingId, userId));
        stats.setFollowersCount(followersCount != null ? followersCount.intValue() : 0);

        Long mutualCount = followMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .exists("SELECT 1 FROM user_follow uf2 WHERE uf2.follower_id = user_follow.following_id AND uf2.following_id = user_follow.follower_id"));
        stats.setMutualCount(mutualCount != null ? mutualCount.intValue() : 0);

        if (currentUserId != null && !currentUserId.equals(userId)) {
            Long count = followMapper.selectCount(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUserId)
                            .eq(UserFollow::getFollowingId, userId));
            stats.setIsFollowing(count != null && count > 0);
        } else {
            stats.setIsFollowing(false);
        }

        return stats;
    }

    @Override
    public Page<FollowUserDTO> getFollowers(Long userId, Integer page, Integer size, Long currentUserId) {
        Page<UserFollow> followPage = new Page<>(page, size);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowingId, userId)
                .orderByDesc(UserFollow::getCreateTime);

        Page<UserFollow> result = followMapper.selectPage(followPage, wrapper);
        List<FollowUserDTO> dtoList = result.getRecords().stream()
                .map(follow -> buildFollowUserDTO(follow.getFollowerId(), currentUserId))
                .collect(Collectors.toList());

        Page<FollowUserDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Override
    public Page<FollowUserDTO> getFollowing(Long userId, Integer page, Integer size, Long currentUserId) {
        Page<UserFollow> followPage = new Page<>(page, size);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .orderByDesc(UserFollow::getCreateTime);

        Page<UserFollow> result = followMapper.selectPage(followPage, wrapper);
        List<FollowUserDTO> dtoList = result.getRecords().stream()
                .map(follow -> buildFollowUserDTO(follow.getFollowingId(), currentUserId))
                .collect(Collectors.toList());

        Page<FollowUserDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Override
    public Page<FollowUserDTO> getMutualFollows(Long userId, Integer page, Integer size, Long currentUserId) {
        Page<UserFollow> followPage = new Page<>(page, size);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .exists("SELECT 1 FROM user_follow uf2 WHERE uf2.follower_id = user_follow.following_id AND uf2.following_id = user_follow.follower_id")
                .orderByDesc(UserFollow::getCreateTime);

        Page<UserFollow> result = followMapper.selectPage(followPage, wrapper);
        List<FollowUserDTO> dtoList = result.getRecords().stream()
                .map(follow -> buildFollowUserDTO(follow.getFollowingId(), currentUserId))
                .collect(Collectors.toList());

        Page<FollowUserDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    private FollowUserDTO buildFollowUserDTO(Long targetUserId, Long currentUserId) {
        FollowUserDTO dto = new FollowUserDTO();
        dto.setUserId(targetUserId);

        try {
            Result<UserInfoDTO> userResult = userClient.getUserInfoById(targetUserId);
            if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                UserInfoDTO userInfo = userResult.getData();
                dto.setUsername(userInfo.getUsername());
                dto.setNickname(userInfo.getNickname());
                dto.setAvatar(userInfo.getAvatar());
            }
        } catch (Exception e) {
            log.error("获取用户信息失败, userId: {}", targetUserId, e);
        }

        if (currentUserId != null && !currentUserId.equals(targetUserId)) {
            Long count = followMapper.selectCount(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUserId)
                            .eq(UserFollow::getFollowingId, targetUserId));
            dto.setIsFollowed(count != null && count > 0);
        }

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long postId, Long userId) {
        CommunityPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("动态不存在");
        }
        
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此动态");
        }
        
        post.setStatus(0); // 逻辑删除
        postMapper.updateById(post);
        
        // 从 Elasticsearch 删除
        try {
            elasticsearchUtil.deleteDocument("community_post_index", postId.toString());
        } catch (Exception e) {
            log.error("从ES删除动态失败，动态ID: {}", postId, e);
        }
    }

    @Override
    public PostDTO getPostById(Long id, Long currentUserId) {
        CommunityPost post = postMapper.selectById(id);
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException("动态不存在或已删除");
        }
        return convertToDTO(post, currentUserId);
    }

    @Override
    public Page<PostDTO> searchPosts(String keyword, Integer page, Integer size, Long currentUserId) {
        if (cn.hutool.core.util.StrUtil.isBlank(keyword)) {
            return getHotPosts(page, size, currentUserId);
        }

        int from = (page - 1) * size;
        List<java.util.Map<String, Object>> searchResults = elasticsearchUtil.search("community_post_index", keyword, from, size, "content");

        if (searchResults.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        List<Long> ids = searchResults.stream()
                .map(m -> Long.valueOf(m.get("id").toString()))
                .collect(Collectors.toList());

        List<CommunityPost> posts = postMapper.selectBatchIds(ids);
        java.util.Map<Long, CommunityPost> postMap = posts.stream().collect(Collectors.toMap(CommunityPost::getId, p -> p));
        
        List<CommunityPost> sortedPosts = ids.stream()
                .map(postMap::get)
                .filter(p -> p != null && p.getStatus() == 1)
                .collect(Collectors.toList());

        Page<CommunityPost> postPage = new Page<>(page, size, sortedPosts.size());
        postPage.setRecords(sortedPosts);
        
        return convertToDTOPage(postPage, currentUserId);
    }

    @Override
    public Page<PostDTO> getUserPosts(Long userId, Integer page, Integer size, Long currentUserId) {
        if (userId == null) return new Page<>(page, size, 0);
        Page<CommunityPost> p = new Page<>(page, size);
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityPost::getUserId, userId)
                .eq(CommunityPost::getStatus, 1)
                .orderByDesc(CommunityPost::getCreateTime);
        Page<CommunityPost> result = postMapper.selectPage(p, wrapper);
        return convertToDTOPage(result, currentUserId);
    }

    @Override
    public Page<PostDTO> getUserLikedPosts(Long userId, Integer page, Integer size, Long currentUserId) {
        log.info("查询用户点赞动态列表, userId: {}, page: {}, size: {}, currentUserId: {}", userId, page, size, currentUserId);
        if (userId == null) return new Page<>(page, size, 0);
        List<PostLike> likes = likeMapper.selectList(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, userId)
        );
        log.info("用户 {} 点赞动态记录数: {}", userId, likes.size());
        if (likes.isEmpty()) return new Page<>(page, size, 0);
        List<Long> ids = likes.stream().map(PostLike::getPostId).collect(Collectors.toList());
        log.info("点赞的动态ID列表: {}", ids);
        Page<CommunityPost> p = new Page<>(page, size);
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CommunityPost::getId, ids).eq(CommunityPost::getStatus, 1).orderByDesc(CommunityPost::getCreateTime);
        Page<CommunityPost> result = postMapper.selectPage(p, wrapper);
        log.info("最终返回的动态记录数: {}", result.getRecords().size());
        return convertToDTOPage(result, currentUserId);
    }

    @Override
    public Page<PostDTO> getUserCollectedPosts(Long userId, Integer page, Integer size, Long currentUserId) {
        // Community module doesn't seem to have a collection table for posts yet.
        // For now, return an empty page to avoid 404/500.
        return new Page<>(page, size, 0);
    }

    private Page<PostDTO> convertToDTOPage(Page<CommunityPost> postPage, Long currentUserId) {
        Page<PostDTO> dtoPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        List<PostDTO> dtoList = postPage.getRecords().stream()
                .map(post -> convertToDTO(post, currentUserId))
                .collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    private PostDTO convertToDTO(CommunityPost post, Long currentUserId) {
        PostDTO dto = new PostDTO();
        
        String imagesJson = post.getImages();
        post.setImages(null);
        BeanUtils.copyProperties(post, dto);
        post.setImages(imagesJson);
        
        if (imagesJson != null && !imagesJson.isEmpty()) {
            try {
                List<String> imageList = JSONUtil.toList(imagesJson, String.class);
                dto.setImages(imageList);
                log.info("Post {} images parsed successfully: {}", post.getId(), imageList);
            } catch (Exception e) {
                log.error("Failed to parse images JSON for post {}: {}", post.getId(), imagesJson, e);
                dto.setImages(null);
            }
        }
        
        if (currentUserId != null) {
            dto.setIsLiked(likeMapper.selectCount(
                    new LambdaQueryWrapper<PostLike>()
                            .eq(PostLike::getPostId, post.getId())
                            .eq(PostLike::getUserId, currentUserId)
            ) > 0);
        }

        if (post.getUserId() != null) {
            try {
                Result<UserInfoDTO> userResult = userClient.getUserInfoById(post.getUserId());
                if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                    UserInfoDTO userInfo = userResult.getData();
                    dto.setUsername(userInfo.getUsername());
                    dto.setNickname(userInfo.getNickname());
                    dto.setAvatar(userInfo.getAvatar());
                }
            } catch (Exception e) {
                log.error("获取动态作者信息失败, 用户ID: {}", post.getUserId(), e);
            }
        }

        log.info("convertToDTO result - postId: {}, images: {}, username: {}", 
                dto.getId(), dto.getImages(), dto.getUsername());
        
        return dto;
    }
    
    @Override
    public Page<com.medifood.community.dto.PostCommentDTO> getPostComments(Long postId, Integer page, Integer size, Long currentUserId) {
        Page<PostComment> topPage = new Page<>(page, size);
        LambdaQueryWrapper<PostComment> topWrapper = new LambdaQueryWrapper<>();
        topWrapper.eq(PostComment::getPostId, postId)
                .eq(PostComment::getStatus, 1)
                .eq(PostComment::getParentId, 0L)
                .orderByDesc(PostComment::getCreateTime);
        Page<PostComment> topResult = commentMapper.selectPage(topPage, topWrapper);
        
        java.util.List<Long> topIds = topResult.getRecords().stream().map(PostComment::getId).collect(Collectors.toList());
        java.util.Map<Long, java.util.List<PostComment>> repliesMap = new java.util.HashMap<>();
        if (!topIds.isEmpty()) {
            LambdaQueryWrapper<PostComment> replyWrapper = new LambdaQueryWrapper<>();
            replyWrapper.eq(PostComment::getPostId, postId)
                    .eq(PostComment::getStatus, 1)
                    .in(PostComment::getParentId, topIds)
                    .orderByAsc(PostComment::getCreateTime);
            java.util.List<PostComment> replies = commentMapper.selectList(replyWrapper);
            for (PostComment c : replies) {
                repliesMap.computeIfAbsent(c.getParentId(), k -> new java.util.ArrayList<>()).add(c);
            }
        }
        
        Page<com.medifood.community.dto.PostCommentDTO> dtoPage = new Page<>(topResult.getCurrent(), topResult.getSize(), topResult.getTotal());
        java.util.List<com.medifood.community.dto.PostCommentDTO> dtoList = topResult.getRecords().stream().map(c -> {
            com.medifood.community.dto.PostCommentDTO dto = new com.medifood.community.dto.PostCommentDTO();
            org.springframework.beans.BeanUtils.copyProperties(c, dto);
            dto.setIsLiked(false);
            java.util.List<PostComment> replyList = repliesMap.getOrDefault(c.getId(), java.util.Collections.emptyList());
            java.util.List<com.medifood.community.dto.PostCommentDTO> replyDTOs = replyList.stream().map(r -> {
                com.medifood.community.dto.PostCommentDTO rdto = new com.medifood.community.dto.PostCommentDTO();
                org.springframework.beans.BeanUtils.copyProperties(r, rdto);
                rdto.setIsLiked(false);
                return rdto;
            }).collect(Collectors.toList());
            dto.setReplies(replyDTOs);
            return dto;
        }).collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }
}

