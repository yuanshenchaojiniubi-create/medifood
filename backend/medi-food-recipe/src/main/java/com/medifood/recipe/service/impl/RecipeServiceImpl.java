package com.medifood.recipe.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.audit.ContentAuditEvent;
import com.medifood.common.audit.ContentAuditEventPublisher;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.Result;
import com.medifood.common.result.ResultCode;
import com.medifood.common.sync.EsSyncEvent;
import com.medifood.common.sync.EsSyncEventPublisher;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifood.common.util.SensitiveWordFilter;
import com.medifood.recipe.dto.*;
import com.medifood.recipe.entity.RecipeCollect;
import com.medifood.recipe.entity.RecipeComment;
import com.medifood.recipe.entity.*;
import com.medifood.recipe.feign.UserClient;
import com.medifood.recipe.mapper.*;
import com.medifood.recipe.service.RecipeService;
import com.medifood.recipe.service.RecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 食谱服务实现类
 * 
 * @author MediFood Team
 */
@Slf4j
@Service
public class RecipeServiceImpl implements RecipeService {
    
    @Autowired
    private RecipeMapper recipeMapper;
    
    @Autowired
    private RecipeTagMapper tagMapper;
    
    @Autowired
    private RecipeTagRelationMapper tagRelationMapper;
    
    @Autowired
    private RecipeLikeMapper likeMapper;
    
    @Autowired
    private RecipeCommentMapper commentMapper;

    @Autowired
    private RecipeCollectMapper collectMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private EsSyncEventPublisher esSyncEventPublisher;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private ContentAuditEventPublisher auditEventPublisher;

    /** 热门食谱 Redis 缓存 Key */
    public static final String HOT_RECIPE_CACHE_KEY = "home:hot:recipe";

    /** 热门食谱 ZSet Key */
    public static final String HOT_RECIPE_ZSET_KEY = "recipe:hot:zset";

    /** 缓存默认条数 */
    private static final int HOT_CACHE_SIZE = 10;

    /** 缓存过期时长（小时），远大于刷新间隔，作为安全兜底 */
    private static final long CACHE_TTL_HOURS = 2;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long userId, RecipePublishDTO publishDTO) {
        Result<Boolean> perm = userClient.canPublishContent(userId);
        if (perm == null || !ResultCode.SUCCESS.getCode().equals(perm.getCode())
                || perm.getData() == null || !Boolean.TRUE.equals(perm.getData())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅认证医师可发布养生食谱");
        }

        // 第一层防线：敏感词同步拦截（标题+描述+功效）
        SensitiveWordFilter.check(publishDTO.getTitle(), "食谱标题");
        SensitiveWordFilter.check(publishDTO.getDescription(), "食谱描述");
        SensitiveWordFilter.check(publishDTO.getEffects(), "功效说明");

        Recipe recipe = new Recipe();
        recipe.setUserId(userId);
        recipe.setTitle(publishDTO.getTitle());
        recipe.setDescription(publishDTO.getDescription());
        recipe.setIngredients(JSONUtil.toJsonStr(publishDTO.getIngredients()));
        recipe.setSteps(JSONUtil.toJsonStr(publishDTO.getSteps()));
        recipe.setSuitableFor(publishDTO.getSuitableFor());
        recipe.setEffects(publishDTO.getEffects());
        recipe.setCoverImage(publishDTO.getCoverImage());

        if (publishDTO.getImages() != null && !publishDTO.getImages().isEmpty()) {
            recipe.setImages(JSONUtil.toJsonStr(publishDTO.getImages()));
        }

        recipe.setDifficulty(publishDTO.getDifficulty() != null ? publishDTO.getDifficulty() : 1);
        recipe.setCookingTime(publishDTO.getCookingTime());
        recipe.setServings(publishDTO.getServings());
        recipe.setViewCount(0);
        recipe.setLikeCount(0);
        recipe.setCommentCount(0);
        recipe.setCollectCount(0);
        // 食谱为权威内容，采用"先审后发"：status=3 审核中，AI/管理员审核通过后转为 1 已发布
        recipe.setStatus(3);

        recipeMapper.insert(recipe);
        replaceRecipeTags(recipe.getId(), publishDTO.getTagIds());

        // 加入热门 ZSet 排行榜，初始分为0（热门榜查询时会过滤非已发布状态）
        try {
            stringRedisTemplate.opsForZSet().add(HOT_RECIPE_ZSET_KEY, recipe.getId().toString(), 0);
        } catch (Exception e) {
            log.error("初始化食谱 ZSet 分数失败", e);
        }

        // ES 同步事件携带 status=3，AI 模块监听器发现非发布状态会自动跳过索引，
        // 审核通过后再由审核服务触发重新同步
        publishRecipeSyncEvent(recipe.getId(), EsSyncEvent.SyncType.CREATE);

        log.info("发布食谱成功（待审核），食谱ID: {}, 用户ID: {}", recipe.getId(), userId);

        // 第二层防线：AI 异步审核（先审后发，审核通过自动发布）
        auditEventPublisher.publish(new ContentAuditEvent(
                recipe.getId(), userId, ContentAuditEvent.TYPE_RECIPE,
                recipe.getTitle(), recipe.getDescription(), publishDTO.getImages()));

        return recipe.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateRecipe(Long recipeId, Long userId, RecipePublishDTO publishDTO) {
        Result<Boolean> perm = userClient.canPublishContent(userId);
        if (perm == null || !ResultCode.SUCCESS.getCode().equals(perm.getCode())
                || perm.getData() == null || !Boolean.TRUE.equals(perm.getData())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅认证医师可编辑养生食谱");
        }

        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null || !Objects.equals(recipe.getStatus(), 1)) {
            throw new BusinessException(ResultCode.RECIPE_NOT_EXIST);
        }
        if (!Objects.equals(recipe.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 敏感词同步拦截（编辑后的内容同样需要过滤）
        SensitiveWordFilter.check(publishDTO.getTitle(), "食谱标题");
        SensitiveWordFilter.check(publishDTO.getDescription(), "食谱描述");
        SensitiveWordFilter.check(publishDTO.getEffects(), "功效说明");

        recipe.setTitle(publishDTO.getTitle());
        recipe.setDescription(publishDTO.getDescription());
        recipe.setIngredients(JSONUtil.toJsonStr(publishDTO.getIngredients()));
        recipe.setSteps(JSONUtil.toJsonStr(publishDTO.getSteps()));
        recipe.setSuitableFor(publishDTO.getSuitableFor());
        recipe.setEffects(publishDTO.getEffects());
        recipe.setCoverImage(publishDTO.getCoverImage());
        recipe.setImages((publishDTO.getImages() == null || publishDTO.getImages().isEmpty())
                ? null : JSONUtil.toJsonStr(publishDTO.getImages()));
        recipe.setDifficulty(publishDTO.getDifficulty() != null ? publishDTO.getDifficulty() : recipe.getDifficulty());
        recipe.setCookingTime(publishDTO.getCookingTime());
        recipe.setServings(publishDTO.getServings());
        recipeMapper.updateById(recipe);

        replaceRecipeTags(recipeId, publishDTO.getTagIds());
        publishRecipeSyncEvent(recipeId, EsSyncEvent.SyncType.UPDATE);

        // 编辑后的内容同样进入 AI 异步审核（内容保持在线，判定违规时自动删除）
        auditEventPublisher.publish(new ContentAuditEvent(
                recipeId, userId, ContentAuditEvent.TYPE_RECIPE,
                recipe.getTitle(), recipe.getDescription(), publishDTO.getImages()));

        log.info("更新食谱成功，食谱ID: {}, 用户ID: {}", recipeId, userId);
        return recipeId;
    }
    
    @Override
    public Page<RecipeDTO> getRecipeList(RecipeQueryDTO queryDTO, Long currentUserId) {
        Page<Recipe> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(Recipe::getStatus, 1);
        
        if (StrUtil.isNotBlank(queryDTO.getKeyword())) {
            wrapper.and(w -> w.like(Recipe::getTitle, queryDTO.getKeyword())
                    .or().like(Recipe::getDescription, queryDTO.getKeyword()));
        }
        
        // 标签筛选
        if (queryDTO.getTagIds() != null && !queryDTO.getTagIds().isEmpty()) {
            List<Long> recipeIds = tagRelationMapper.selectList(
                    new LambdaQueryWrapper<RecipeTagRelation>()
                            .in(RecipeTagRelation::getTagId, queryDTO.getTagIds())
            ).stream().map(RecipeTagRelation::getRecipeId).distinct().collect(Collectors.toList());
            
            if (recipeIds.isEmpty()) {
                return new Page<>(queryDTO.getPage(), queryDTO.getSize());
            }
            wrapper.in(Recipe::getId, recipeIds);
        }
        
        if ("hot".equals(queryDTO.getSort())) {
            wrapper.orderByDesc(Recipe::getLikeCount)
                    .orderByDesc(Recipe::getViewCount);
        } else {
            wrapper.orderByDesc(Recipe::getCreateTime);
        }
        
        Page<Recipe> recipePage = recipeMapper.selectPage(page, wrapper);
        Page<RecipeDTO> resultPage = new Page<>(recipePage.getCurrent(), recipePage.getSize(), recipePage.getTotal());
        
        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(recipePage.getRecords());
        List<RecipeDTO> dtoList = recipePage.getRecords().stream()
                .map(r -> convertToDTO(r, currentUserId, userInfoMap, false))
                .collect(Collectors.toList());
        resultPage.setRecords(dtoList);
        
        return resultPage;
    }
    
    @Override
    public Page<RecipeDTO> searchRecipe(String keyword, Integer page, Integer size, Long currentUserId) {
        log.info("搜索食谱，关键词: {}, page: {}, size: {}", keyword, page, size);
        if (StrUtil.isBlank(keyword)) {
            RecipeQueryDTO queryDTO = new RecipeQueryDTO();
            queryDTO.setPage(page);
            queryDTO.setSize(size);
            return getRecipeList(queryDTO, currentUserId);
        }

        int from = (page - 1) * size;
        List<Map<String, Object>> searchRecords = elasticsearchUtil.search(
                "recipe_index", keyword, from, size, "title", "description", "ingredients", "steps", "effects");

        if (searchRecords.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        List<Long> ids = searchRecords.stream()
                .map(m -> {
                    Object idObj = m.get("id");
                    if (idObj == null) return null;
                    return Long.valueOf(idObj.toString());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("从ES获取到ID列表: {}", ids);

        if (ids.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        // 从数据库获取完整信息以保持一致性并按顺序排列
        List<Recipe> recipeList = recipeMapper.selectBatchIds(ids);
        log.info("从数据库查询到食谱数量: {}", recipeList.size());
        Map<Long, Recipe> recipeMap = recipeList.stream()
                .collect(Collectors.toMap(Recipe::getId, r -> r, (existing, replacement) -> existing));
        
        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(recipeList);
        List<RecipeDTO> dtoList = new ArrayList<>();
        for (Long id : ids) {
            Recipe r = recipeMap.get(id);
            if (r != null && r.getStatus() == 1) {
                try {
                    dtoList.add(convertToDTO(r, currentUserId, userInfoMap, false));
                } catch (Exception e) {
                    log.error("转换食谱DTO失败，ID: {}", id, e);
                }
            } else {
                log.warn("食谱在数据库中不存在或状态非发布，ID: {}", id);
            }
        }

        Page<RecipeDTO> resultPage = new Page<>(page, size, searchRecords.size());
        resultPage.setRecords(dtoList);

        return resultPage;
    }
    
    @Override
    public RecipeDTO getRecipeDetail(Long id, Long currentUserId) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null || recipe.getStatus() != 1) {
            throw new BusinessException(ResultCode.RECIPE_NOT_EXIST);
        }
        
        // 增加浏览次数
        recipeMapper.incrementViewCount(id);
        
        // 更新热门排行分
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_RECIPE_ZSET_KEY, id.toString(), 1);
        } catch (Exception e) {
            log.error("更新食谱浏览量 ZSet 分数失败", e);
        }
        
        return convertToDTO(recipe, currentUserId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeRecipe(Long recipeId, Long userId) {
        LambdaQueryWrapper<RecipeLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeLike::getRecipeId, recipeId)
                .eq(RecipeLike::getUserId, userId);
        
        if (likeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经点赞过了");
        }
        
        RecipeLike like = new RecipeLike();
        like.setRecipeId(recipeId);
        like.setUserId(userId);
        likeMapper.insert(like);
        
        recipeMapper.incrementLikeCount(recipeId);
        
        // 更新热门排行分 (点赞权重3)
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_RECIPE_ZSET_KEY, recipeId.toString(), 3);
        } catch (Exception e) {
            log.error("更新食谱点赞 ZSet 分数失败", e);
        }

        // 清除推荐缓存（用户行为变化，推荐需要刷新）
        recommendationService.clearCache(userId);
        
        log.info("点赞食谱成功，食谱ID: {}, 用户ID: {}", recipeId, userId);
        try {
            Recipe r = recipeMapper.selectById(recipeId);
            if (r != null && r.getUserId() != null && !r.getUserId().equals(userId)) {
                com.medifood.recipe.notify.Notification n = new com.medifood.recipe.notify.Notification();
                n.setRecipientId(r.getUserId());
                n.setActorId(userId);
                n.setType(1);
                n.setEntityType(2);
                n.setEntityId(recipeId);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeRecipe(Long recipeId, Long userId) {
        LambdaQueryWrapper<RecipeLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeLike::getRecipeId, recipeId)
                .eq(RecipeLike::getUserId, userId);
        
        RecipeLike like = likeMapper.selectOne(wrapper);
        if (like == null) {
            throw new BusinessException("还未点赞");
        }
        
        likeMapper.deleteById(like.getId());
        recipeMapper.decrementLikeCount(recipeId);
        
        // 更新热门排行分 (取消点赞减3)
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_RECIPE_ZSET_KEY, recipeId.toString(), -3);
        } catch (Exception e) {
            log.error("更新食谱取消点赞 ZSet 分数失败", e);
        }

        // 清除推荐缓存
        recommendationService.clearCache(userId);
        
        log.info("取消点赞成功，食谱ID: {}, 用户ID: {}", recipeId, userId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void collectRecipe(Long recipeId, Long userId) {
        LambdaQueryWrapper<RecipeCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeCollect::getRecipeId, recipeId)
                .eq(RecipeCollect::getUserId, userId);
        
        if (collectMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经收藏过了");
        }
        
        RecipeCollect collect = new RecipeCollect();
        collect.setRecipeId(recipeId);
        collect.setUserId(userId);
        collectMapper.insert(collect);
        
        recipeMapper.incrementCollectCount(recipeId);
        log.info("收藏食谱成功，食谱ID: {}, 用户ID: {}", recipeId, userId);

        // 清除推荐缓存
        recommendationService.clearCache(userId);
        try {
            Recipe r = recipeMapper.selectById(recipeId);
            if (r != null && r.getUserId() != null && !r.getUserId().equals(userId)) {
                com.medifood.recipe.notify.Notification n = new com.medifood.recipe.notify.Notification();
                n.setRecipientId(r.getUserId());
                n.setActorId(userId);
                n.setType(5); // 5:收藏
                n.setEntityType(2); // 2:食谱
                n.setEntityId(recipeId);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uncollectRecipe(Long recipeId, Long userId) {
        LambdaQueryWrapper<RecipeCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeCollect::getRecipeId, recipeId)
                .eq(RecipeCollect::getUserId, userId);
        
        RecipeCollect collect = collectMapper.selectOne(wrapper);
        if (collect == null) {
            throw new BusinessException("还未收藏");
        }
        
        collectMapper.deleteById(collect.getId());
        recipeMapper.decrementCollectCount(recipeId);
        log.info("取消收藏成功，食谱ID: {}, 用户ID: {}", recipeId, userId);

        // 清除推荐缓存
        recommendationService.clearCache(userId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commentRecipe(Long userId, Long recipeId, String content, Long parentId) {
        // 第一层防线：敏感词同步拦截
        SensitiveWordFilter.check(content, "评论内容");

        RecipeComment comment = new RecipeComment();
        comment.setRecipeId(recipeId);
        comment.setUserId(userId);
        comment.setParentId(parentId != null ? parentId : 0L);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setStatus(1);

        commentMapper.insert(comment);
        recipeMapper.incrementCommentCount(recipeId);

        // 更新热门排行分 (评论权重2)
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_RECIPE_ZSET_KEY, recipeId.toString(), 2);
        } catch (Exception e) {
            log.error("更新食谱评论 ZSet 分数失败", e);
        }

        log.info("评论食谱成功，食谱ID: {}, 用户ID: {}", recipeId, userId);

        // 第二层防线：AI 异步审核（先发后审）
        auditEventPublisher.publish(new ContentAuditEvent(
                comment.getId(), userId, ContentAuditEvent.TYPE_RECIPE_COMMENT,
                null, content, null));

        try {
            Recipe r = recipeMapper.selectById(recipeId);
            if (r != null && r.getUserId() != null && !r.getUserId().equals(userId)) {
                com.medifood.recipe.notify.Notification n = new com.medifood.recipe.notify.Notification();
                n.setRecipientId(r.getUserId());
                n.setActorId(userId);
                n.setType(2);
                n.setEntityType(2);
                n.setEntityId(recipeId);
                n.setExtraId(parentId != null ? parentId : 0L);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
            if (parentId != null && parentId > 0) {
                RecipeComment parent = commentMapper.selectById(parentId);
                if (parent != null && parent.getUserId() != null && !parent.getUserId().equals(userId)) {
                    com.medifood.recipe.notify.Notification rn = new com.medifood.recipe.notify.Notification();
                    rn.setRecipientId(parent.getUserId());
                    rn.setActorId(userId);
                    rn.setType(3);
                    rn.setEntityType(2);
                    rn.setEntityId(recipeId);
                    rn.setExtraId(parentId);
                    rn.setIsRead(0);
                    rn.setCreateTime(new java.util.Date());
                    notificationService.send(rn);
                }
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecipe(Long recipeId, Long userId) {
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new BusinessException(ResultCode.RECIPE_NOT_EXIST);
        }
        
        if (!recipe.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        recipe.setStatus(2);
        recipeMapper.updateById(recipe);

        // 异步从 Elasticsearch 删除
        try {
            esSyncEventPublisher.publishDelete(
                    recipeId,
                    EsSyncEvent.EntityType.RECIPE,
                    "recipe_index",
                    buildRecipeVersion(recipe),
                    null
            );
        } catch (Exception e) {
            log.error("发送食谱ES删除消息失败，食谱ID: {}", recipeId, e);
        }

        // 从 ZSet 移除
        try {
            stringRedisTemplate.opsForZSet().remove(HOT_RECIPE_ZSET_KEY, recipeId.toString());
        } catch (Exception e) {
            log.error("从 ZSet 移除食谱失败", e);
        }

        log.info("删除食谱成功，食谱ID: {}, 用户ID: {}", recipeId, userId);
    }
    
    private RecipeDTO convertToDTO(Recipe recipe, Long currentUserId) {
        return convertToDTO(recipe, currentUserId, null, true);
    }

    private RecipeDTO convertToDTO(Recipe recipe, Long currentUserId,
                                   Map<Long, UserInfoDTO> userInfoMap, boolean allowRemoteUserQuery) {
        RecipeDTO dto = new RecipeDTO();
        BeanUtils.copyProperties(recipe, dto);
        
        if (StrUtil.isNotBlank(recipe.getIngredients())) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ingredients = (List<Map<String, Object>>) (List<?>) JSONUtil.toList(recipe.getIngredients(), Object.class);
            dto.setIngredients(ingredients);
        }
        
        if (StrUtil.isNotBlank(recipe.getSteps())) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) (List<?>) JSONUtil.toList(recipe.getSteps(), Object.class);
            dto.setSteps(steps);
        }
        
        if (StrUtil.isNotBlank(recipe.getImages())) {
            dto.setImages(JSONUtil.toList(recipe.getImages(), String.class));
        }
        
        // 查询标签
        List<RecipeTagRelation> relations = tagRelationMapper.selectList(
                new LambdaQueryWrapper<RecipeTagRelation>()
                        .eq(RecipeTagRelation::getRecipeId, recipe.getId())
        );
        List<String> tagNames = relations.stream()
                .map(relation -> {
                    RecipeTag tag = tagMapper.selectById(relation.getTagId());
                    return tag != null ? tag.getTagName() : null;
                })
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        dto.setTags(tagNames);
        
        if (currentUserId != null) {
            dto.setIsLiked(likeMapper.selectCount(
                    new LambdaQueryWrapper<RecipeLike>()
                            .eq(RecipeLike::getRecipeId, recipe.getId())
                            .eq(RecipeLike::getUserId, currentUserId)
            ) > 0);
            dto.setIsCollected(collectMapper.selectCount(
                    new LambdaQueryWrapper<RecipeCollect>()
                            .eq(RecipeCollect::getRecipeId, recipe.getId())
                            .eq(RecipeCollect::getUserId, currentUserId)
            ) > 0);
        }
        
        // 优先从本地缓存取用户信息，避免同一页重复远程调用
        UserInfoDTO cachedUser = null;
        if (recipe.getUserId() != null && userInfoMap != null) {
            cachedUser = userInfoMap.get(recipe.getUserId());
        }
        if (cachedUser != null) {
            dto.setUsername(cachedUser.getUsername());
            dto.setNickname(cachedUser.getNickname());
            dto.setAvatar(cachedUser.getAvatar());
            dto.setIsDoctor(cachedUser.getIsDoctor());
        } else if (allowRemoteUserQuery && recipe.getUserId() != null && userClient != null) {
            try {
                Result<UserInfoDTO> userResult = userClient.getUserInfoById(recipe.getUserId());
                if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                    dto.setUsername(userResult.getData().getUsername());
                    dto.setNickname(userResult.getData().getNickname());
                    dto.setAvatar(userResult.getData().getAvatar());
                    dto.setIsDoctor(userResult.getData().getIsDoctor());
                }
            } catch (Exception e) {
                log.error("获取食谱作者信息失败, 用户ID: {}", recipe.getUserId(), e);
            }
        }
        
        return dto;
    }
    @Override
    public Page<RecipeCommentDTO> getCommentList(Long recipeId, Integer page, Integer size, Long currentUserId) {
        Page<RecipeComment> commentPage = new Page<>(page, size);
        LambdaQueryWrapper<RecipeComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeComment::getRecipeId, recipeId)
                .eq(RecipeComment::getParentId, 0)
                .eq(RecipeComment::getStatus, 1)
                .orderByDesc(RecipeComment::getCreateTime);

        Page<RecipeComment> result = commentMapper.selectPage(commentPage, wrapper);

        Page<RecipeCommentDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<RecipeCommentDTO> dtoList = result.getRecords().stream()
                .map(comment -> convertCommentToDTO(comment, currentUserId))
                .collect(Collectors.toList());

        // 加载回复
        for (RecipeCommentDTO dto : dtoList) {
            List<RecipeComment> replies = commentMapper.selectList(
                    new LambdaQueryWrapper<RecipeComment>()
                            .eq(RecipeComment::getParentId, dto.getId())
                            .eq(RecipeComment::getStatus, 1)
                            .orderByAsc(RecipeComment::getCreateTime)
            );
            dto.setReplies(replies.stream()
                    .map(reply -> convertCommentToDTO(reply, currentUserId))
                    .collect(Collectors.toList()));
        }

        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        RecipeComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() == 0) {
            throw new BusinessException("评论不存在");
        }
        
        // 权限检查：只有评论者本人可以删除评论
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        // 逻辑删除
        comment.setStatus(0);
        commentMapper.updateById(comment);
        
        // 同步更新食谱评论数
        recipeMapper.decrementCommentCount(comment.getRecipeId());
        
        // 更新热门排行分 (删除评论减2)
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_RECIPE_ZSET_KEY, comment.getRecipeId().toString(), -2);
        } catch (Exception e) {
            log.error("更新食谱删除评论 ZSet 分数失败", e);
        }
        
        log.info("删除评论成功，评论ID: {}, 用户ID: {}", commentId, userId);
    }

    /**
     * 转换评论为 DTO
     */
    private RecipeCommentDTO convertCommentToDTO(RecipeComment comment, Long currentUserId) {
        RecipeCommentDTO dto = new RecipeCommentDTO();
        BeanUtils.copyProperties(comment, dto);
        dto.setReplies(new ArrayList<>());

        // 填充用户信息
        if (comment.getUserId() != null && userClient != null) {
            try {
                Result<UserInfoDTO> userResult = userClient.getUserInfoById(comment.getUserId());
                if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                    dto.setUsername(userResult.getData().getUsername());
                    dto.setNickname(userResult.getData().getNickname());
                    dto.setAvatar(userResult.getData().getAvatar());
                }
            } catch (Exception e) {
                log.error("获取评论用户信息失败, 用户ID: {}", comment.getUserId(), e);
            }
        }

        // 检查是否点赞
        if (currentUserId != null) {
            // TODO: 实现评论点赞功能
            dto.setIsLiked(false);
        }

        return dto;
    }

    @Override
    public Page<RecipeDTO> getUserPublishedRecipes(Long userId, Integer page, Integer size, Long currentUserId) {
        if (userId == null) return new Page<>(page, size, 0);
        Page<Recipe> p = new Page<>(page, size);
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Recipe::getUserId, userId)
                .eq(Recipe::getStatus, 1)
                .orderByDesc(Recipe::getCreateTime);
        Page<Recipe> result = recipeMapper.selectPage(p, wrapper);
        return convertToDTOPage(result, currentUserId);
    }

    @Autowired
    private com.medifood.recipe.notify.NotificationServiceImpl notificationService;

    @Override
    public Page<RecipeDTO> getUserLikedRecipes(Long userId, Integer page, Integer size, Long currentUserId) {
        log.info("查询用户点赞食谱列表, userId: {}, page: {}, size: {}, currentUserId: {}", userId, page, size, currentUserId);
        if (userId == null) return new Page<>(page, size, 0);
        
        List<RecipeLike> likes = likeMapper.selectList(
                new LambdaQueryWrapper<RecipeLike>()
                        .eq(RecipeLike::getUserId, userId)
        );
        log.info("用户 {} 在数据库中的点赞食谱记录数: {}", userId, likes.size());
        
        List<Long> ids = likes.stream()
                .map(RecipeLike::getRecipeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        log.info("用户 {} 点赞的有效食谱 ID 列表: {}", userId, ids);
        
        if (ids.isEmpty()) {
            log.info("用户 {} 没有点赞任何有效食谱，返回空页面", userId);
            return new Page<>(page, size, 0);
        }
        
        Page<Recipe> p = new Page<>(page, size);
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Recipe::getId, ids)
                .eq(Recipe::getStatus, 1)
                .orderByDesc(Recipe::getCreateTime);
        
        Page<Recipe> result = recipeMapper.selectPage(p, wrapper);
        log.info("最终查询到的有效食谱记录数: {}", result.getRecords().size());
        
        // 打印食谱标题方便调试
        result.getRecords().forEach(r -> log.info("点赞列表中的食谱: ID={}, Title={}", r.getId(), r.getTitle()));
        
        return convertToDTOPage(result, currentUserId);
    }

    @Override
    public List<RecipeTag> getAllTags() {
        return tagMapper.selectList(
                new LambdaQueryWrapper<RecipeTag>()
                        .orderByAsc(RecipeTag::getTagType)
                        .orderByAsc(RecipeTag::getTagName)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecipeTag createTag(String tagName, String tagType) {
        if (StrUtil.isBlank(tagName)) {
            throw new BusinessException("标签名称不能为空");
        }
        RecipeTag existing = tagMapper.selectOne(
                new LambdaQueryWrapper<RecipeTag>()
                        .eq(RecipeTag::getTagName, tagName)
        );
        if (existing != null) {
            return existing;
        }
        RecipeTag tag = new RecipeTag();
        tag.setTagName(tagName);
        tag.setTagType(StrUtil.isNotBlank(tagType) ? tagType : "custom");
        tagMapper.insert(tag);
        log.info("创建食谱标签成功: {}", tagName);
        return tag;
    }

    @Override
    public Page<RecipeDTO> getUserCollectedRecipes(Long userId, Integer page, Integer size, Long currentUserId) {
        log.info("查询用户收藏食谱列表, userId: {}, page: {}, size: {}, currentUserId: {}", userId, page, size, currentUserId);
        if (userId == null) return new Page<>(page, size, 0);
        List<RecipeCollect> collects = collectMapper.selectList(
                new LambdaQueryWrapper<RecipeCollect>()
                        .eq(RecipeCollect::getUserId, userId)
        );
        log.info("用户 {} 收藏食谱记录数: {}", userId, collects.size());
        if (collects.isEmpty()) return new Page<>(page, size, 0);
        List<Long> ids = collects.stream().map(RecipeCollect::getRecipeId).collect(Collectors.toList());
        log.info("收藏的食谱ID列表: {}", ids);
        Page<Recipe> p = new Page<>(page, size);
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Recipe::getId, ids).eq(Recipe::getStatus, 1).orderByDesc(Recipe::getCreateTime);
        Page<Recipe> result = recipeMapper.selectPage(p, wrapper);
        log.info("最终返回的收藏食谱记录数: {}", result.getRecords().size());
        return convertToDTOPage(result, currentUserId);
    }

    private final Object hotCacheLock = new Object();

    @Override
    public List<RecipeDTO> getHotRecipes(Integer size, Long currentUserId) {
        if (size == null || size <= 0) {
            size = HOT_CACHE_SIZE;
        }

        // ① 优先读 Redis 缓存（O(1)，完全不查数据库）
        try {
            String cached = stringRedisTemplate.opsForValue().get(HOT_RECIPE_CACHE_KEY);
            if (StrUtil.isNotBlank(cached)) {
                List<RecipeDTO> list = JSONUtil.toList(cached, RecipeDTO.class);
                log.debug("热门食谱命中 Redis 缓存，返回 {} 条", list.size());
                return list.subList(0, Math.min(size, list.size()));
            }
        } catch (Exception e) {
            log.warn("读取热门食谱 Redis 缓存失败: {}", e.getMessage());
        }

        // ② 缓存未命中，加锁防止缓存击穿（并发风暴）
        synchronized (hotCacheLock) {
            try {
                // 双重检查锁（Double-Checked Locking）
                String cached = stringRedisTemplate.opsForValue().get(HOT_RECIPE_CACHE_KEY);
                if (StrUtil.isNotBlank(cached)) {
                    List<RecipeDTO> list = JSONUtil.toList(cached, RecipeDTO.class);
                    log.debug("热门食谱命中 Redis 缓存(加锁后)，返回 {} 条", list.size());
                    return list.subList(0, Math.min(size, list.size()));
                }

                // 只有拿到锁的第一个线程去刷新缓存
                log.info("缓存为空，当前线程获取到锁，准备刷新热门食谱缓存...");
                refreshHotRecipeCache();
                
                cached = stringRedisTemplate.opsForValue().get(HOT_RECIPE_CACHE_KEY);
                if (StrUtil.isNotBlank(cached)) {
                    List<RecipeDTO> list = JSONUtil.toList(cached, RecipeDTO.class);
                    return list.subList(0, Math.min(size, list.size()));
                }
            } catch (Exception e) {
                log.warn("加锁刷新热门食谱缓存失败，准备降级查库: {}", e.getMessage());
            }

            // ③ 极端情况兜底：直接查库（放在锁内，防止并发压垮数据库和用户服务）
            log.warn("热门食谱缓存未命中且刷新失败，降级直接查库");
            LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Recipe::getStatus, 1)
                    .last("ORDER BY (like_count * 3 + view_count + comment_count * 2) DESC LIMIT " + size);
            List<Recipe> dbList = recipeMapper.selectList(wrapper);
            Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(dbList);
            return dbList.stream().map(r -> convertToDTO(r, null, userInfoMap, false)).collect(Collectors.toList());
        }
    }

    @Override
    public void refreshHotRecipeCache() {
        log.info("开始刷新热门食谱 Redis 缓存...");
        try {
            // 1. 尝试从 ZSet 获取前 N 名的 ID
            java.util.Set<String> topIdsStr = stringRedisTemplate.opsForZSet().reverseRange(HOT_RECIPE_ZSET_KEY, 0, HOT_CACHE_SIZE - 1);
            List<Recipe> list = new ArrayList<>();
            
            if (topIdsStr != null && !topIdsStr.isEmpty()) {
                List<Long> ids = topIdsStr.stream().map(Long::valueOf).collect(Collectors.toList());
                // 使用 selectBatchIds 获取详情，避免全表扫描
                List<Recipe> dbList = recipeMapper.selectBatchIds(ids);
                Map<Long, Recipe> map = dbList.stream().collect(Collectors.toMap(Recipe::getId, r -> r));
                // 按照 ZSet 返回的顺序组装
                for (Long id : ids) {
                    Recipe r = map.get(id);
                    if (r != null && r.getStatus() == 1) {
                        list.add(r);
                    }
                }
                log.info("通过 ZSet 获取了 {} 条热门食谱", list.size());
            } else {
                // 如果 ZSet 为空（例如首次启动），降级为全表扫描并初始化 ZSet
                log.info("ZSet 为空，降级全表扫描获取热门食谱并初始化 ZSet");
                LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Recipe::getStatus, 1)
                        .last("ORDER BY (like_count * 3 + view_count + comment_count * 2) DESC LIMIT " + HOT_CACHE_SIZE);
                list = recipeMapper.selectList(wrapper);
                
                // 初始化 ZSet
                for (Recipe r : list) {
                    double score = r.getLikeCount() * 3.0 + r.getViewCount() * 1.0 + r.getCommentCount() * 2.0;
                    stringRedisTemplate.opsForZSet().add(HOT_RECIPE_ZSET_KEY, r.getId().toString(), score);
                }
            }

            // 缓存时不携带用户个人状态（isLiked / isCollected），首页展示无需此信息
            Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(list);
            List<RecipeDTO> dtoList = list.stream()
                    .map(r -> convertToDTO(r, null, userInfoMap, false))
                    .collect(Collectors.toList());

            String json = JSONUtil.toJsonStr(dtoList);
            stringRedisTemplate.opsForValue().set(
                    HOT_RECIPE_CACHE_KEY,
                    json,
                    CACHE_TTL_HOURS, TimeUnit.HOURS);

            log.info("热门食谱缓存刷新完成，共 {} 条，TTL={}h", dtoList.size(), CACHE_TTL_HOURS);
        } catch (Exception e) {
            log.error("刷新热门食谱 Redis 缓存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void syncAllToES() {
        log.info("开始同步所有食谱到 Elasticsearch...");
        List<Recipe> recipes = recipeMapper.selectList(new LambdaQueryWrapper<Recipe>().eq(Recipe::getStatus, 1));

        int syncCount = 0;

        for (Recipe recipe : recipes) {
            try {
                esSyncEventPublisher.publishUpdate(
                        recipe.getId(),
                        EsSyncEvent.EntityType.RECIPE,
                        "recipe_index",
                        buildRecipeVersion(recipe),
                        buildRecipePayload(recipe)
                );
                syncCount++;
            } catch (Exception e) {
                log.error("发送食谱ES同步消息失败，食谱ID: {}", recipe.getId(), e);
            }
        }
        log.info("食谱同步消息发送完成，共计 {} 条，成功发送 {} 条。", recipes.size(), syncCount);
    }

    @Override
    public Page<RecipeDTO> searchBySeason(String seasonName, Integer page, Integer size, Long currentUserId) {
        log.info("按节气查询食谱: seasonName={}, page={}, size={}", seasonName, page, size);
        if (StrUtil.isBlank(seasonName)) {
            return new Page<>(page, size, 0);
        }

        // 使用 FIND_IN_SET 精确匹配 suitable_seasons 字段
        List<Recipe> seasonRecipes = recipeMapper.findBySeason(seasonName, size * 3);
        log.info("节气精确匹配到 {} 条食谱", seasonRecipes.size());

        if (seasonRecipes.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(seasonRecipes);
        List<RecipeDTO> dtoList = seasonRecipes.stream()
                .map(r -> convertToDTO(r, currentUserId, userInfoMap, false))
                .collect(Collectors.toList());

        // 手动分页
        int total = dtoList.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        if (fromIndex >= total) {
            return new Page<>(page, size, total);
        }

        Page<RecipeDTO> resultPage = new Page<>(page, size, total);
        resultPage.setRecords(dtoList.subList(fromIndex, toIndex));
        return resultPage;
    }

    private Page<RecipeDTO> convertToDTOPage(Page<Recipe> page, Long currentUserId) {
        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(page.getRecords());
        Page<RecipeDTO> dtoPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<RecipeDTO> dtoList = page.getRecords().stream()
                .map(r -> convertToDTO(r, currentUserId, userInfoMap, false))
                .collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    /**
     * 批量构建当前页涉及用户信息，避免同一页重复远程调用 user-service。
     */
    private Map<Long, UserInfoDTO> buildUserInfoMap(List<Recipe> records) {
        if (records == null || records.isEmpty() || userClient == null) {
            return new HashMap<>();
        }
        Map<Long, UserInfoDTO> userInfoMap = new HashMap<>();
        List<Long> userIds = records.stream()
                .map(Recipe::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (Long userId : userIds) {
            try {
                Result<UserInfoDTO> userResult = userClient.getUserInfoById(userId);
                if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                    userInfoMap.put(userId, userResult.getData());
                }
            } catch (Exception e) {
                log.warn("批量构建用户信息失败, userId={}", userId, e);
            }
        }
        return userInfoMap;
    }

    private Map<String, Object> buildRecipePayload(Recipe recipe) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", recipe.getId());
        payload.put("title", recipe.getTitle());
        payload.put("description", recipe.getDescription());
        payload.put("ingredients", recipe.getIngredients());
        payload.put("steps", recipe.getSteps());
        payload.put("suitableFor", recipe.getSuitableFor());
        payload.put("effects", recipe.getEffects());
        payload.put("difficulty", recipe.getDifficulty());
        payload.put("cookingTime", recipe.getCookingTime());
        payload.put("servings", recipe.getServings());
        payload.put("userId", recipe.getUserId());
        payload.put("coverImage", recipe.getCoverImage());
        payload.put("images", parseStringList(recipe.getImages()));
        payload.put("ingredientTexts", buildStructuredRecipeTexts(recipe.getIngredients(), "name", "ingredient", "amount", "value"));
        payload.put("stepTexts", buildStructuredRecipeTexts(recipe.getSteps(), "description", "content", "step", "title"));
        payload.put("tagNames", tagRelationMapper.selectList(
                        new LambdaQueryWrapper<RecipeTagRelation>()
                                .eq(RecipeTagRelation::getRecipeId, recipe.getId()))
                .stream()
                .map(RecipeTagRelation::getTagId)
                .map(tagMapper::selectById)
                .filter(Objects::nonNull)
                .map(RecipeTag::getTagName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList()));
        payload.put("status", recipe.getStatus());
        payload.put("updateTime", recipe.getUpdateTime() != null ? recipe.getUpdateTime().toString() : null);
        return payload;
    }

    private void replaceRecipeTags(Long recipeId, List<Long> tagIds) {
        tagRelationMapper.delete(new LambdaQueryWrapper<RecipeTagRelation>()
                .eq(RecipeTagRelation::getRecipeId, recipeId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            if (tagId == null) {
                continue;
            }
            RecipeTagRelation relation = new RecipeTagRelation();
            relation.setRecipeId(recipeId);
            relation.setTagId(tagId);
            tagRelationMapper.insert(relation);
        }
    }

    /**
     * 内部接口：触发食谱 ES 重新同步（内容审核通过后由审核服务 Feign 调用）
     */
    @Override
    public void resyncEs(Long recipeId) {
        publishRecipeSyncEvent(recipeId, EsSyncEvent.SyncType.UPDATE);
    }

    private void publishRecipeSyncEvent(Long recipeId, EsSyncEvent.SyncType syncType) {
        Recipe savedRecipe = recipeMapper.selectById(recipeId);
        if (savedRecipe == null) {
            return;
        }
        try {
            if (syncType == EsSyncEvent.SyncType.CREATE) {
                esSyncEventPublisher.publishCreate(
                        savedRecipe.getId(),
                        EsSyncEvent.EntityType.RECIPE,
                        "recipe_index",
                        buildRecipeVersion(savedRecipe),
                        buildRecipePayload(savedRecipe)
                );
            } else {
                esSyncEventPublisher.publishUpdate(
                        savedRecipe.getId(),
                        EsSyncEvent.EntityType.RECIPE,
                        "recipe_index",
                        buildRecipeVersion(savedRecipe),
                        buildRecipePayload(savedRecipe)
                );
            }
        } catch (Exception e) {
            log.error("发送食谱ES同步消息失败，食谱ID: {}", recipeId, e);
        }
    }

    private Long buildRecipeVersion(Recipe recipe) {
        if (recipe == null) {
            return System.currentTimeMillis();
        }
        if (recipe.getUpdateTime() != null) {
            return recipe.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (recipe.getCreateTime() != null) {
            return recipe.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return recipe.getId();
    }

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(json, String.class);
    }

    private List<String> buildStructuredRecipeTexts(String json, String... preferredKeys) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        List<String> results = new ArrayList<>();
        List<?> items = JSONUtil.toList(json, Object.class);
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            if (item instanceof Map<?, ?> rawMap) {
                String text = buildMapSummary(rawMap, preferredKeys);
                if (StrUtil.isNotBlank(text)) {
                    results.add(text);
                }
            } else {
                String text = String.valueOf(item).trim();
                if (!text.isEmpty()) {
                    results.add(text);
                }
            }
        }
        return results;
    }

    private String buildMapSummary(Map<?, ?> rawMap, String... preferredKeys) {
        List<String> parts = new ArrayList<>();
        for (String key : preferredKeys) {
            Object value = rawMap.get(key);
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
                parts.add(String.valueOf(value));
            }
        }
        if (!parts.isEmpty()) {
            return String.join(" ", parts);
        }
        return rawMap.values().stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));
    }

    @Override
    public List<RecipeDTO> recommend(Long userId, Integer size, Long currentUserId) {
        if (size == null || size <= 0) {
            size = 10;
        }

        List<Long> recommendedIds = recommendationService.recommend(userId, size);

        if (recommendedIds.isEmpty()) {
            // 推荐算法无结果，降级为热门食谱
            return getHotRecipes(size, currentUserId);
        }

        // 批量查询食谱详情
        List<Recipe> recipes = recipeMapper.selectBatchIds(recommendedIds);
        Map<Long, Recipe> recipeMap = recipes.stream()
                .collect(Collectors.toMap(Recipe::getId, r -> r));

        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(recipes);

        // 按推荐顺序组装结果
        List<RecipeDTO> dtoList = new ArrayList<>();
        for (Long id : recommendedIds) {
            Recipe r = recipeMap.get(id);
            if (r != null && r.getStatus() == 1) {
                dtoList.add(convertToDTO(r, currentUserId, userInfoMap, false));
            }
        }

        log.info("个性化推荐完成: userId={}, 请求={}条, 实际={}条", userId, size, dtoList.size());
        return dtoList;
    }
}
