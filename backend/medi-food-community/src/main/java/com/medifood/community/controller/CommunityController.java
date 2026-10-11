package com.medifood.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.notification.NotificationEvent;
import com.medifood.common.notification.NotificationEventPublisher;
import com.medifood.common.result.Result;
import com.medifood.community.dto.FollowStatsDTO;
import com.medifood.community.dto.FollowUserDTO;
import com.medifood.community.dto.PostDTO;
import com.medifood.community.dto.PostPublishDTO;
import com.medifood.community.feign.UserClient;
import com.medifood.community.notify.NotificationService;
import com.medifood.community.notify.PublishNotificationDTO;
import com.medifood.community.service.CommunityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/community")
public class CommunityController {
    
    @Autowired
    private CommunityService communityService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationEventPublisher notificationEventPublisher;
    @Autowired
    private UserClient userClient;
    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final RestTemplate restTemplate = new RestTemplate();
    
    @PostMapping("/post")
    public Result<Long> publishPost(@CurrentUser Long userId, 
                                   @Validated @RequestBody PostPublishDTO publishDTO) {
        Long postId = communityService.publishPost(userId, publishDTO);
        return Result.success("发布成功", postId);
    }
    
    @GetMapping("/feed")
    public Result<Page<PostDTO>> getFeed(@CurrentUser(required = false) Long userId,
                                          @RequestParam(defaultValue = "1",name="page") Integer page,
                                          @RequestParam(defaultValue = "10",name="size") Integer size,
                                          @RequestParam(defaultValue = "true", name = "fallback") boolean fallbackToHot) {
        Page<PostDTO> feed = communityService.getFeed(userId != null ? userId : 0L, page, size, fallbackToHot);
        return Result.success(feed);
    }
    
    @GetMapping("/hot")
    public Result<Page<PostDTO>> getHotPosts(@RequestParam(defaultValue = "1",name = "page")Integer page,
                                              @RequestParam(defaultValue = "10",name = "size") Integer size,
                                              @CurrentUser(required = false) Long currentUserId) {
        Page<PostDTO> hotPosts = communityService.getHotPosts(page, size, currentUserId);
        return Result.success(hotPosts);
    }
    
    @PostMapping("/{id}/like")
    public Result<?> likePost(@PathVariable("id") Long id, @CurrentUser Long userId) {
        communityService.likePost(id, userId);
        try {
            Long recipient = communityService.getPostById(id, userId).getUserId();
            log.info("构建点赞通知: postId={}, actorId={}, recipientId={}", id, userId, recipient);
            if (recipient != null && !recipient.equals(userId)) {
                notificationEventPublisher.publish(new NotificationEvent(
                        recipient, userId, 1, 3, id, 0L));
            }
        } catch (Exception e) {
            log.warn("发送点赞通知失败: postId={}, error={}", id, e.toString());
        }
        return Result.success("点赞成功");
    }
    
    @DeleteMapping("/{id}/like")
    public Result<?> unlikePost(@PathVariable("id") Long id, @CurrentUser Long userId) {
        communityService.unlikePost(id, userId);
        return Result.success("取消点赞成功");
    }
    
    @PostMapping("/{id}/comment")
    public Result<?> commentPost(@PathVariable("id") Long id,
                                 @CurrentUser Long userId,
                                 @RequestParam(name = "content") String content,
                                 @RequestParam(required = false, name = "parentId") Long parentId) {
        communityService.commentPost(userId, id, content, parentId);
        try {
            Long recipient = communityService.getPostById(id, userId).getUserId();
            log.info("构建评论通知: postId={}, actorId={}, recipientId={}, parentId={} ", id, userId, recipient, parentId);
            if (recipient != null && !recipient.equals(userId)) {
                notificationEventPublisher.publish(new NotificationEvent(
                        recipient, userId, 2, 3, id, parentId != null ? parentId : 0L));
            }
        } catch (Exception e) {
            log.warn("发送评论通知失败: postId={}, error={}", id, e.toString());
        }
        return Result.success("评论成功");
    }
    
    @PostMapping("/user/{userId}/follow")
    public Result<?> followUser(@PathVariable("userId") Long userId, @CurrentUser Long currentUserId) {
        communityService.followUser(currentUserId, userId);
        try {
            if (!userId.equals(currentUserId)) {
                notificationEventPublisher.publish(new NotificationEvent(
                        userId, currentUserId, 4, 3, currentUserId, 0L));
            }
        } catch (Exception e) {
            log.warn("发送关注通知失败: userId={}, error={}", userId, e.toString());
        }
        return Result.success("关注成功");
    }
    
    @DeleteMapping("/user/{userId}/follow")
    public Result<?> unfollowUser(@PathVariable("userId") Long userId, @CurrentUser Long currentUserId) {
        communityService.unfollowUser(currentUserId, userId);
        return Result.success("取消关注成功");
    }

    @GetMapping("/user/{userId}/follow-stats")
    public Result<FollowStatsDTO> getFollowStats(@PathVariable("userId") Long userId,
                                                  @RequestParam(required = false) Long currentUserId) {
        FollowStatsDTO stats = communityService.getFollowStats(userId, currentUserId);
        return Result.success(stats);
    }

    @GetMapping("/user/{userId}/followers")
    public Result<Page<FollowUserDTO>> getFollowers(@PathVariable("userId") Long userId,
                                            @RequestParam(defaultValue = "1",name = "page") Integer page,
                                            @RequestParam(defaultValue = "10",name = "size") Integer size,
                                            @CurrentUser(required = false) Long currentUserId) {
        Page<FollowUserDTO> followers = communityService.getFollowers(userId, page, size, currentUserId);
        return Result.success(followers);
    }
    
    @GetMapping("/user/{userId}/following")
    public Result<Page<FollowUserDTO>> getFollowing(@PathVariable("userId") Long userId,
                                            @RequestParam(defaultValue = "1",name = "page") Integer page,
                                            @RequestParam(defaultValue = "10",name = "size") Integer size,
                                            @CurrentUser(required = false) Long currentUserId) {
        Page<FollowUserDTO> following = communityService.getFollowing(userId, page, size, currentUserId);
        return Result.success(following);
    }

    @GetMapping("/user/{userId}/mutual")
    public Result<Page<FollowUserDTO>> getMutualFollows(@PathVariable("userId") Long userId,
                                                        @RequestParam(defaultValue = "1", name = "page") Integer page,
                                                        @RequestParam(defaultValue = "10", name = "size") Integer size,
                                                        @CurrentUser(required = false) Long currentUserId) {
        Page<FollowUserDTO> mutual = communityService.getMutualFollows(userId, page, size, currentUserId);
        return Result.success(mutual);
    }

    @DeleteMapping("/post/{id}")
    public Result<?> deletePost(@PathVariable("id") Long id, @CurrentUser Long userId) {
        communityService.deletePost(id, userId);
        return Result.success("删除成功");
    }

    /**
     * 获取动态详情
     */
    @GetMapping("/post/{id}")
    public Result<PostDTO> getPostById(@PathVariable("id") Long id, @CurrentUser(required = false) Long currentUserId) {
        PostDTO post = communityService.getPostById(id, currentUserId);
        return Result.success(post);
    }
    
    @GetMapping("/post/{id}/comments")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.medifood.community.dto.PostCommentDTO>> getPostComments(@PathVariable("id") Long id,
                                                                                              @RequestParam(defaultValue = "1") Integer page,
                                                                                              @RequestParam(defaultValue = "10") Integer size,
                                                                                              @CurrentUser(required = false) Long currentUserId) {
        return Result.success(communityService.getPostComments(id, page, size, currentUserId));
    }

    @GetMapping("/search")
    public Result<Page<PostDTO>> searchPosts(@RequestParam String keyword,
                                             @RequestParam(defaultValue = "1", name = "page") Integer page,
                                             @RequestParam(defaultValue = "10", name = "size") Integer size,
                                             @CurrentUser(required = false) Long currentUserId) {
        Page<PostDTO> result = communityService.searchPosts(keyword, page, size, currentUserId);
        return Result.success(result);
    }

    /**
     * 获取用户发布的动态
     */
    @GetMapping("/user/{userId}/posts")
    public Result<Page<PostDTO>> getUserPosts(@PathVariable("userId") Long userId,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer size,
                                             @CurrentUser(required = false) Long currentUserId) {
        Page<PostDTO> res = communityService.getUserPosts(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 获取用户点赞的动态
     */
    @GetMapping("/user/{userId}/liked")
    public Result<Page<PostDTO>> getUserLikedPosts(@PathVariable("userId") Long userId,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer size,
                                                  @CurrentUser(required = false) Long currentUserId) {
        Page<PostDTO> res = communityService.getUserLikedPosts(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 获取用户收藏的动态
     */
    @GetMapping("/user/{userId}/collected")
    public Result<Page<PostDTO>> getUserCollectedPosts(@PathVariable("userId") Long userId,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @CurrentUser(required = false) Long currentUserId) {
        Page<PostDTO> res = communityService.getUserCollectedPosts(userId, page, size, currentUserId);
        return Result.success(res);
    }

    @GetMapping("/notify/list")
    public Result<Page<Map<String, Object>>> listNotifications(@CurrentUser Long userId,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(notificationService.list(userId, page, size));
    }

    @PostMapping("/notify/read/{id}")
    public Result<?> markRead(@CurrentUser Long userId, @PathVariable Long id) {
        notificationService.markRead(userId, id);
        return Result.success();
    }

    @PostMapping("/notify/read-aggregate")
    public Result<?> markAggregateRead(@CurrentUser Long userId, @RequestParam("key") String aggregateKey) {
        notificationService.markAggregateRead(userId, aggregateKey);
        return Result.success();
    }

    @PostMapping("/notify/read-all")
    public Result<?> markAllRead(@CurrentUser Long userId) {
        notificationService.markAllRead(userId);
        return Result.success();
    }

    @GetMapping("/notify/unread-count")
    public Result<Long> unreadCount(@CurrentUser Long userId) {
        return Result.success(notificationService.unreadCount(userId));
    }

    @DeleteMapping("/notify/{id}")
    public Result<?> deleteNotification(@CurrentUser Long userId, @PathVariable Long id) {
        notificationService.delete(userId, id);
        return Result.success();
    }

    @DeleteMapping("/notify/aggregate")
    public Result<?> deleteAggregateNotification(@CurrentUser Long userId, @RequestParam("key") String aggregateKey) {
        notificationService.deleteAggregate(userId, aggregateKey);
        return Result.success();
    }

    @PostMapping("/notify/publish")
    public Result<?> publishNotification(@Validated @RequestBody PublishNotificationDTO dto) {
        if (dto.getRecipientId() == null || dto.getActorId() == null || dto.getEntityType() == null || dto.getType() == null || dto.getEntityId() == null) {
            return Result.fail("缺少必要参数");
        }
        if (dto.getRecipientId().equals(dto.getActorId())) {
            return Result.success(); // 不推送给自己
        }
        notificationEventPublisher.publish(new NotificationEvent(
                dto.getRecipientId(),
                dto.getActorId(),
                dto.getType(),
                dto.getEntityType(),
                dto.getEntityId(),
                dto.getExtraId() != null ? dto.getExtraId() : 0L
        ));
        return Result.success();
    }

    @GetMapping("/notify/stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
            @CurrentUser Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "uid", required = false) Long uid
    ) {
        Long effectiveUserId = userId != null ? userId : (headerUserId != null ? headerUserId : uid);
        if (effectiveUserId == null) {
            throw new RuntimeException("Unauthorized: missing user id for SSE");
        }
        log.info("SSE 注册: effectiveUserId={}", effectiveUserId);
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        notificationService.register(effectiveUserId, emitter);
        emitter.onCompletion(() -> notificationService.unregister(effectiveUserId));
        emitter.onTimeout(() -> notificationService.unregister(effectiveUserId));
        return emitter;
    }

    /**
     * 批量获取通知富文本数据（替代前端N+1查询）
     * <p>
     * 前端一次性传入所有 actorIds 和 entityInfos，
     * 后端批量查询用户名称和实体标题，大幅减少HTTP请求次数。
     * </p>
     */
    @PostMapping("/notify/enrich")
    public Result<Map<String, Object>> enrichNotifications(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        Map<Long, String> actorNames = new HashMap<>();
        Map<String, String> entityTitles = new HashMap<>();

        // 批量获取用户名称
        List<Integer> actorIdList = (List<Integer>) body.getOrDefault("actorIds", Collections.emptyList());
        if (actorIdList != null && !actorIdList.isEmpty()) {
            for (Object idObj : actorIdList) {
                Long actorId = toLong(idObj);
                if (actorId == null) continue;
                try {
                    com.medifood.common.result.Result<com.medifood.community.dto.UserInfoDTO> r =
                            userClient.getUserInfoById(actorId);
                    if (r != null && r.getCode() == 200 && r.getData() != null) {
                        String name = r.getData().getNickname();
                        if (name == null || name.isEmpty()) {
                            name = r.getData().getUsername();
                        }
                        actorNames.put(actorId, name);
                    }
                } catch (Exception e) {
                    log.warn("获取用户名失败: actorId={}", actorId);
                }
            }
        }

        // 批量获取实体标题
        List<Map<String, Object>> entityInfoList = (List<Map<String, Object>>) body.getOrDefault("entityInfos", Collections.emptyList());
        if (entityInfoList != null && !entityInfoList.isEmpty()) {
            for (Map<String, Object> info : entityInfoList) {
                Integer entityType = toInt(info.get("type"));
                Long entityId = toLong(info.get("id"));
                if (entityType == null || entityId == null) continue;
                String key = entityType + ":" + entityId;
                String title = fetchEntityTitle(entityType, entityId);
                if (title != null && !title.isEmpty()) {
                    entityTitles.put(key, title);
                }
            }
        }

        result.put("actorNames", actorNames);
        result.put("entityTitles", entityTitles);
        return Result.success(result);
    }

    private String fetchEntityTitle(int entityType, Long entityId) {
        try {
            String url = null;
            String titleField = "title";
            switch (entityType) {
                case 1: // 知识
                    url = "http://medi-food-knowledge/knowledge/" + entityId;
                    break;
                case 2: // 食谱
                    url = "http://medi-food-recipe/recipe/" + entityId;
                    break;
                case 3: // 动态
                    url = "http://medi-food-community/community/post/" + entityId;
                    titleField = "content";
                    break;
                case 4: // 问答
                    url = "http://medi-food-qa/qa/question/" + entityId;
                    break;
                default:
                    return null;
            }
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            if (resp.getBody() != null && resp.getBody().get("data") != null) {
                Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
                Object title = data.get(titleField);
                if (title != null) {
                    String titleStr = String.valueOf(title);
                    return titleStr.length() > 30 ? titleStr.substring(0, 30) + "..." : titleStr;
                }
            }
        } catch (Exception e) {
            log.warn("获取实体标题失败: entityType={}, entityId={}", entityType, entityId);
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取聚合通知的所有参与者（谁赞了我/收藏了我）
     * <p>
     * 用户点击聚合通知后可查看具体有哪些人执行了操作。
     * 数据从 Redis 中读取，包含用户名和头像。
     * </p>
     */
    @GetMapping("/notify/aggregate-actors")
    public Result<List<Map<String, Object>>> getAggregateActors(
            @CurrentUser Long userId,
            @RequestParam("key") String aggregateKey) {
        List<Map<String, Object>> actors = new ArrayList<>();

        // 从 Redis 获取参与者ID列表
        try {
            String actorsKey = "notify:agg:actors:" + aggregateKey;
            Set<String> actorIds = stringRedisTemplate != null
                    ? stringRedisTemplate.opsForSet().members(actorsKey)
                    : null;
            if (actorIds == null || actorIds.isEmpty()) {
                // 从数据库获取 actorSample
                AggregateParts parts = parseAggKey(aggregateKey);
                if (parts != null) {
                    String sample = notificationService.getAggregateActorSample(aggregateKey);
                    if (sample != null && !sample.isEmpty()) {
                        actorIds = new HashSet<>(Arrays.asList(sample.split(",")));
                    }
                }
            }
            if (actorIds == null || actorIds.isEmpty()) {
                return Result.success(actors);
            }

            for (String idStr : actorIds) {
                Long actorId = toLong(idStr);
                if (actorId == null) continue;
                Map<String, Object> actor = new HashMap<>();
                actor.put("userId", actorId);
                try {
                    com.medifood.common.result.Result<com.medifood.community.dto.UserInfoDTO> r =
                            userClient.getUserInfoById(actorId);
                    if (r != null && r.getCode() == 200 && r.getData() != null) {
                        actor.put("nickname", r.getData().getNickname() != null
                                ? r.getData().getNickname() : r.getData().getUsername());
                        actor.put("avatar", r.getData().getAvatar());
                    } else {
                        actor.put("nickname", "用户" + actorId);
                    }
                } catch (Exception e) {
                    actor.put("nickname", "用户" + actorId);
                }
                actors.add(actor);
            }
        } catch (Exception e) {
            log.warn("获取聚合参与者失败: key={}", aggregateKey, e);
        }

        return Result.success(actors);
    }

    private AggregateParts parseAggKey(String aggregateKey) {
        if (aggregateKey == null) return null;
        String[] parts = aggregateKey.split(":");
        if (parts.length != 4) return null;
        try {
            AggregateParts p = new AggregateParts();
            p.recipientId = Long.parseLong(parts[0]);
            p.type = Integer.parseInt(parts[1]);
            p.entityType = Integer.parseInt(parts[2]);
            p.entityId = Long.parseLong(parts[3]);
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static class AggregateParts {
        Long recipientId;
        Integer type;
        Integer entityType;
        Long entityId;
    }
}

