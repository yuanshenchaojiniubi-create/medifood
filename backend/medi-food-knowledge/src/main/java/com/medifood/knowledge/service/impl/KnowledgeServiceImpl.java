package com.medifood.knowledge.service.impl;

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
import com.medifood.knowledge.dto.*;
import com.medifood.knowledge.entity.*;
import com.medifood.knowledge.feign.UserClient;
import com.medifood.knowledge.mapper.*;
import com.medifood.knowledge.service.KnowledgeService;
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
 * 知识服务实现类
 * 
 * @author MediFood Team
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {
    
    @Autowired
    private KnowledgeMapper knowledgeMapper;
    
    @Autowired
    private KnowledgeCategoryMapper categoryMapper;
    
    @Autowired
    private KnowledgeIngredientMapper ingredientMapper;
    
    @Autowired
    private KnowledgeLikeMapper likeMapper;
    
    @Autowired
    private KnowledgeCollectMapper collectMapper;
    
    @Autowired
    private KnowledgeCommentMapper commentMapper;
    @Autowired
    private UserClient userClient;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private EsSyncEventPublisher esSyncEventPublisher;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ContentAuditEventPublisher auditEventPublisher;

    /** 热门知识 Redis 缓存 Key */
    public static final String HOT_KNOWLEDGE_CACHE_KEY = "home:hot:knowledge";

    /** 热门知识 ZSet Key */
    public static final String HOT_KNOWLEDGE_ZSET_KEY = "knowledge:hot:zset";

    /** 缓存默认条数 */
    private static final int HOT_CACHE_SIZE = 10;

    /** 缓存过期时长（小时），远大于刷新间隔，作为安全兜底 */
    private static final long CACHE_TTL_HOURS = 2;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long userId, KnowledgePublishDTO publishDTO) {
        Result<Boolean> perm = userClient.canPublishContent(userId);
        if (perm == null || !ResultCode.SUCCESS.getCode().equals(perm.getCode())
                || perm.getData() == null || !Boolean.TRUE.equals(perm.getData())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅认证医师可发布养生知识");
        }

        // 第一层防线：敏感词同步拦截（标题+正文）
        SensitiveWordFilter.check(publishDTO.getTitle(), "知识标题");
        SensitiveWordFilter.check(publishDTO.getContent(), "知识内容");

        Knowledge knowledge = new Knowledge();
        knowledge.setUserId(userId);
        knowledge.setTitle(publishDTO.getTitle());
        knowledge.setContent(publishDTO.getContent());
        knowledge.setCategoryId(publishDTO.getCategoryId());
        knowledge.setCoverImage(publishDTO.getCoverImage());

        // 处理图片列表
        if (publishDTO.getImages() != null && !publishDTO.getImages().isEmpty()) {
            knowledge.setImages(JSONUtil.toJsonStr(publishDTO.getImages()));
        }

        knowledge.setViewCount(0);
        knowledge.setLikeCount(0);
        knowledge.setCommentCount(0);
        knowledge.setCollectCount(0);
        // 知识为权威内容，采用"先审后发"：status=3 审核中，AI/管理员审核通过后转为 1 已发布
        knowledge.setStatus(3);

        knowledgeMapper.insert(knowledge);
        replaceKnowledgeIngredients(knowledge.getId(), publishDTO.getIngredients());
        // 发送异步ES同步消息（payload 携带 status=3，AI 模块监听器发现非发布状态会自动跳过索引，
        // 审核通过后再由审核服务触发重新同步）
        publishKnowledgeSyncEvent(knowledge.getId(), EsSyncEvent.SyncType.CREATE);

        // 加入热门 ZSet 排行榜，初始分为0（热门榜查询时会过滤非已发布状态）
        try {
            stringRedisTemplate.opsForZSet().add(HOT_KNOWLEDGE_ZSET_KEY, knowledge.getId().toString(), 0);
        } catch (Exception e) {
            log.error("初始化知识 ZSet 分数失败", e);
        }

        log.info("发布知识成功（待审核），知识ID: {}, 用户ID: {}", knowledge.getId(), userId);

        // 第二层防线：AI 异步审核（先审后发，审核通过自动发布）
        auditEventPublisher.publish(new ContentAuditEvent(
                knowledge.getId(), userId, ContentAuditEvent.TYPE_KNOWLEDGE,
                knowledge.getTitle(), knowledge.getContent(), publishDTO.getImages()));

        return knowledge.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateKnowledge(Long knowledgeId, Long userId, KnowledgePublishDTO publishDTO) {
        Result<Boolean> perm = userClient.canPublishContent(userId);
        if (perm == null || !ResultCode.SUCCESS.getCode().equals(perm.getCode())
                || perm.getData() == null || !Boolean.TRUE.equals(perm.getData())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅认证医师可编辑养生知识");
        }

        Knowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null || !Objects.equals(knowledge.getStatus(), 1)) {
            throw new BusinessException(ResultCode.KNOWLEDGE_NOT_EXIST);
        }
        if (!Objects.equals(knowledge.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 敏感词同步拦截（编辑后的内容同样需要过滤）
        SensitiveWordFilter.check(publishDTO.getTitle(), "知识标题");
        SensitiveWordFilter.check(publishDTO.getContent(), "知识内容");

        knowledge.setTitle(publishDTO.getTitle());
        knowledge.setContent(publishDTO.getContent());
        knowledge.setCategoryId(publishDTO.getCategoryId());
        knowledge.setCoverImage(publishDTO.getCoverImage());
        knowledge.setImages((publishDTO.getImages() == null || publishDTO.getImages().isEmpty())
                ? null : JSONUtil.toJsonStr(publishDTO.getImages()));
        knowledgeMapper.updateById(knowledge);

        replaceKnowledgeIngredients(knowledgeId, publishDTO.getIngredients());
        publishKnowledgeSyncEvent(knowledgeId, EsSyncEvent.SyncType.UPDATE);

        // 编辑后的内容同样进入 AI 异步审核（内容保持在线，判定违规时自动删除）
        auditEventPublisher.publish(new ContentAuditEvent(
                knowledgeId, userId, ContentAuditEvent.TYPE_KNOWLEDGE,
                knowledge.getTitle(), knowledge.getContent(), publishDTO.getImages()));

        log.info("更新知识成功，知识ID: {}, 用户ID: {}", knowledgeId, userId);
        return knowledgeId;
    }
    
    @Override
    public Page<KnowledgeDTO> getKnowledgeList(KnowledgeQueryDTO queryDTO, Long currentUserId) {
        Page<Knowledge> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        
        // 只查询已发布的知识
        wrapper.eq(Knowledge::getStatus, 1);
        
        // 分类筛选
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(Knowledge::getCategoryId, queryDTO.getCategoryId());
        }
        
        // 关键词搜索
        if (StrUtil.isNotBlank(queryDTO.getKeyword())) {
            wrapper.and(w -> w.like(Knowledge::getTitle, queryDTO.getKeyword())
                    .or().like(Knowledge::getContent, queryDTO.getKeyword()));
        }
        
        // 排序
        if ("hot".equals(queryDTO.getSort())) {
            wrapper.orderByDesc(Knowledge::getLikeCount)
                    .orderByDesc(Knowledge::getViewCount);
        } else {
            wrapper.orderByDesc(Knowledge::getCreateTime);
        }
        
        Page<Knowledge> knowledgePage = knowledgeMapper.selectPage(page, wrapper);
        System.out.println("查询结果："+ knowledgePage.getTotal());
        
        // 食材筛选（在内存中筛选，实际项目中建议在数据库层面处理）
        List<Knowledge> knowledgeList = knowledgePage.getRecords();
        if (StrUtil.isNotBlank(queryDTO.getIngredient())) {
            knowledgeList = knowledgeList.stream()
                    .filter(k -> {
                        List<KnowledgeIngredient> ingredients = ingredientMapper.selectList(
                                new LambdaQueryWrapper<KnowledgeIngredient>()
                                        .eq(KnowledgeIngredient::getKnowledgeId, k.getId())
                        );
                        return ingredients.stream()
                                .anyMatch(ing -> ing.getIngredientName().contains(queryDTO.getIngredient()));
                    })
                    .toList();
        }
        
        // 转换为 DTO
        Page<KnowledgeDTO> resultPage = new Page<>(knowledgePage.getCurrent(), knowledgePage.getSize(), knowledgePage.getTotal());
        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(knowledgeList);
        List<KnowledgeDTO> dtoList = knowledgeList.stream()
                .map(k -> convertToDTO(k, currentUserId, userInfoMap))
                .collect(Collectors.toList());
        resultPage.setRecords(dtoList);
        
        return resultPage;
    }
    
    @Override
    public Page<KnowledgeDTO> searchKnowledge(String keyword, Integer page, Integer size, Long currentUserId) {
        if (StrUtil.isBlank(keyword)) {
            KnowledgeQueryDTO queryDTO = new KnowledgeQueryDTO();
            queryDTO.setPage(page);
            queryDTO.setSize(size);
            return getKnowledgeList(queryDTO, currentUserId);
        }

        int from = (page - 1) * size;
        List<Map<String, Object>> searchRecords = elasticsearchUtil.search(
                "knowledge_index", keyword, from, size, "title", "content");

        if (searchRecords.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        List<Long> ids = searchRecords.stream()
                .map(m -> {
                    Object idObj = m.get("id");
                    if (idObj == null) return null;
                    return Long.valueOf(idObj.toString());
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        List<Knowledge> knowledgeList = knowledgeMapper.selectBatchIds(ids);
        java.util.Map<Long, Knowledge> map = knowledgeList.stream()
                .collect(Collectors.toMap(Knowledge::getId, k -> k, (existing, replacement) -> existing));
        
        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(knowledgeList);
        List<KnowledgeDTO> dtoList = ids.stream()
                .map(id -> convertToDTO(map.get(id), currentUserId, userInfoMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Page<KnowledgeDTO> resultPage = new Page<>(page, size, searchRecords.size());
        resultPage.setRecords(dtoList);

        return resultPage;
    }
    
    @Override
    public KnowledgeDTO getKnowledgeDetail(Long id, Long currentUserId) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null || knowledge.getStatus() != 1) {
            throw new BusinessException(ResultCode.KNOWLEDGE_NOT_EXIST);
        }
        
        // 增加浏览次数 (自己开启隐式事务)
        knowledgeMapper.incrementViewCount(id);
        
        // 更新热门排行分
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_KNOWLEDGE_ZSET_KEY, id.toString(), 1);
        } catch (Exception e) {
            log.error("更新知识浏览量 ZSet 分数失败", e);
        }
        
        return convertToDTO(knowledge, currentUserId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeKnowledge(Long knowledgeId, Long userId) {
        // 检查是否已点赞
        LambdaQueryWrapper<KnowledgeLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeLike::getKnowledgeId, knowledgeId)
                .eq(KnowledgeLike::getUserId, userId);
        
        if (likeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经点赞过了");
        }
        
        // 添加点赞记录
        KnowledgeLike like = new KnowledgeLike();
        like.setKnowledgeId(knowledgeId);
        like.setUserId(userId);
        likeMapper.insert(like);
        
        // 更新点赞数
        knowledgeMapper.incrementLikeCount(knowledgeId);
        
        // 更新热门排行分 (点赞权重3)
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_KNOWLEDGE_ZSET_KEY, knowledgeId.toString(), 3);
        } catch (Exception e) {
            log.error("更新知识点赞 ZSet 分数失败", e);
        }
        
        log.info("点赞知识成功，知识ID: {}, 用户ID: {}", knowledgeId, userId);
        try {
            Knowledge k = knowledgeMapper.selectById(knowledgeId);
            if (k != null && k.getUserId() != null && !k.getUserId().equals(userId)) {
                com.medifood.knowledge.notify.Notification n = new com.medifood.knowledge.notify.Notification();
                n.setRecipientId(k.getUserId());
                n.setActorId(userId);
                n.setType(1);
                n.setEntityType(1);
                n.setEntityId(knowledgeId);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeKnowledge(Long knowledgeId, Long userId) {
        LambdaQueryWrapper<KnowledgeLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeLike::getKnowledgeId, knowledgeId)
                .eq(KnowledgeLike::getUserId, userId);
        
        KnowledgeLike like = likeMapper.selectOne(wrapper);
        if (like == null) {
            throw new BusinessException("还未点赞");
        }
        
        likeMapper.deleteById(like.getId());
        knowledgeMapper.decrementLikeCount(knowledgeId);
        
        // 更新热门排行分 (取消点赞减3)
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_KNOWLEDGE_ZSET_KEY, knowledgeId.toString(), -3);
        } catch (Exception e) {
            log.error("更新知识取消点赞 ZSet 分数失败", e);
        }
        
        log.info("取消点赞成功，知识ID: {}, 用户ID: {}", knowledgeId, userId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void collectKnowledge(Long knowledgeId, Long userId) {
        LambdaQueryWrapper<KnowledgeCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCollect::getKnowledgeId, knowledgeId)
                .eq(KnowledgeCollect::getUserId, userId);
        
        if (collectMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经收藏过了");
        }
        
        KnowledgeCollect collect = new KnowledgeCollect();
        collect.setKnowledgeId(knowledgeId);
        collect.setUserId(userId);
        collectMapper.insert(collect);
        
        knowledgeMapper.incrementCollectCount(knowledgeId);
        
        log.info("收藏知识成功，知识ID: {}, 用户ID: {}", knowledgeId, userId);
        try {
            Knowledge k = knowledgeMapper.selectById(knowledgeId);
            if (k != null && k.getUserId() != null && !k.getUserId().equals(userId)) {
                com.medifood.knowledge.notify.Notification n = new com.medifood.knowledge.notify.Notification();
                n.setRecipientId(k.getUserId());
                n.setActorId(userId);
                n.setType(5); // 5:收藏
                n.setEntityType(1); // 1:知识
                n.setEntityId(knowledgeId);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uncollectKnowledge(Long knowledgeId, Long userId) {
        LambdaQueryWrapper<KnowledgeCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCollect::getKnowledgeId, knowledgeId)
                .eq(KnowledgeCollect::getUserId, userId);
        
        KnowledgeCollect collect = collectMapper.selectOne(wrapper);
        if (collect == null) {
            throw new BusinessException("还未收藏");
        }
        
        collectMapper.deleteById(collect.getId());
        knowledgeMapper.decrementCollectCount(knowledgeId);
        
        log.info("取消收藏成功，知识ID: {}, 用户ID: {}", knowledgeId, userId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeCommentDTO commentKnowledge(Long userId, KnowledgeCommentCreateDTO commentDTO) {
        // 第一层防线：敏感词同步拦截
        SensitiveWordFilter.check(commentDTO.getContent(), "评论内容");

        KnowledgeComment comment = new KnowledgeComment();
        comment.setKnowledgeId(commentDTO.getKnowledgeId());
        comment.setUserId(userId);
        comment.setParentId(commentDTO.getParentId() != null ? commentDTO.getParentId() : 0L);
        comment.setContent(commentDTO.getContent());
        comment.setLikeCount(0);
        comment.setStatus(1);

        commentMapper.insert(comment);
        
        // 更新评论数
        knowledgeMapper.incrementCommentCount(commentDTO.getKnowledgeId());
        
        // 更新热门排行分 (评论权重2)
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_KNOWLEDGE_ZSET_KEY, commentDTO.getKnowledgeId().toString(), 2);
        } catch (Exception e) {
            log.error("更新知识评论 ZSet 分数失败", e);
        }
        
        log.info("评论知识成功，知识ID: {}, 用户ID: {}", commentDTO.getKnowledgeId(), userId);

        // 第二层防线：AI 异步审核（先发后审）
        auditEventPublisher.publish(new ContentAuditEvent(
                comment.getId(), userId, ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT,
                null, comment.getContent(), null));

        try {
            Knowledge k = knowledgeMapper.selectById(commentDTO.getKnowledgeId());
            if (k != null && k.getUserId() != null && !k.getUserId().equals(userId)) {
                com.medifood.knowledge.notify.Notification n = new com.medifood.knowledge.notify.Notification();
                n.setRecipientId(k.getUserId());
                n.setActorId(userId);
                n.setType(2);
                n.setEntityType(1);
                n.setEntityId(commentDTO.getKnowledgeId());
                n.setExtraId(commentDTO.getParentId() != null ? commentDTO.getParentId() : 0L);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
            if (commentDTO.getParentId() != null && commentDTO.getParentId() > 0) {
                KnowledgeComment parent = commentMapper.selectById(commentDTO.getParentId());
                if (parent != null && parent.getUserId() != null && !parent.getUserId().equals(userId)) {
                    com.medifood.knowledge.notify.Notification rn = new com.medifood.knowledge.notify.Notification();
                    rn.setRecipientId(parent.getUserId());
                    rn.setActorId(userId);
                    rn.setType(3);
                    rn.setEntityType(1);
                    rn.setEntityId(commentDTO.getKnowledgeId());
                    rn.setExtraId(commentDTO.getParentId());
                    rn.setIsRead(0);
                    rn.setCreateTime(new java.util.Date());
                    notificationService.send(rn);
                }
            }
        } catch (Exception ignored) {}
        
        return convertCommentToDTO(comment, userId);
    }
    
    @Override
    public Page<KnowledgeCommentDTO> getCommentList(Long knowledgeId, Integer page, Integer size, Long currentUserId) {
        Page<KnowledgeComment> commentPage = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeComment::getKnowledgeId, knowledgeId)
                .eq(KnowledgeComment::getParentId, 0)
                .eq(KnowledgeComment::getStatus, 1)
                .orderByDesc(KnowledgeComment::getCreateTime);
        
        Page<KnowledgeComment> result = commentMapper.selectPage(commentPage, wrapper);
        
        Page<KnowledgeCommentDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<KnowledgeCommentDTO> dtoList = result.getRecords().stream()
                .map(comment -> convertCommentToDTO(comment, currentUserId))
                .collect(Collectors.toList());
        
        // 加载回复
        for (KnowledgeCommentDTO dto : dtoList) {
            List<KnowledgeComment> replies = commentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeComment>()
                            .eq(KnowledgeComment::getParentId, dto.getId())
                            .eq(KnowledgeComment::getStatus, 1)
                            .orderByAsc(KnowledgeComment::getCreateTime)
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
    public void deleteKnowledge(Long knowledgeId, Long userId) {
        Knowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_NOT_EXIST);
        }
        
        if (!knowledge.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        knowledge.setStatus(2); // 已删除
        knowledgeMapper.updateById(knowledge);

        // 异步从 Elasticsearch 删除
        try {
            esSyncEventPublisher.publishDelete(
                    knowledgeId,
                    EsSyncEvent.EntityType.KNOWLEDGE,
                    "knowledge_index",
                    buildKnowledgeVersion(knowledge),
                    null
            );
        } catch (Exception e) {
            log.error("发送知识ES删除消息失败，知识ID: {}", knowledgeId, e);
        }
        
        // 从 ZSet 移除
        try {
            stringRedisTemplate.opsForZSet().remove(HOT_KNOWLEDGE_ZSET_KEY, knowledgeId.toString());
        } catch (Exception e) {
            log.error("从 ZSet 移除知识失败", e);
        }
        
        log.info("删除知识成功，知识ID: {}, 用户ID: {}", knowledgeId, userId);
    }
    
    /**
     * 转换为 DTO
     */
    private KnowledgeDTO convertToDTO(Knowledge knowledge, Long currentUserId) {
        return convertToDTO(knowledge, currentUserId, null, true);
    }

    private KnowledgeDTO convertToDTO(Knowledge knowledge, Long currentUserId,
                                      Map<Long, UserInfoDTO> userInfoMap, boolean allowRemoteUserQuery) {
        KnowledgeDTO dto = new KnowledgeDTO();
        BeanUtils.copyProperties(knowledge, dto);
        
        // 处理图片列表
        if (StrUtil.isNotBlank(knowledge.getImages())) {
            dto.setImages(JSONUtil.toList(knowledge.getImages(), String.class));
        }
        
        // 查询分类信息
        if (knowledge.getCategoryId() != null) {
            KnowledgeCategory category = categoryMapper.selectById(knowledge.getCategoryId());
            if (category != null) {
                dto.setCategoryName(category.getCategoryName());
            }
        }
        
        // 查询食材列表
        List<KnowledgeIngredient> ingredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<KnowledgeIngredient>()
                        .eq(KnowledgeIngredient::getKnowledgeId, knowledge.getId())
        );
        dto.setIngredients(ingredients.stream()
                .map(KnowledgeIngredient::getIngredientName)
                .collect(Collectors.toList()));
        
        // 检查是否点赞和收藏
        if (currentUserId != null) {
            dto.setIsLiked(likeMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeLike>()
                            .eq(KnowledgeLike::getKnowledgeId, knowledge.getId())
                            .eq(KnowledgeLike::getUserId, currentUserId)
            ) > 0);
            
            dto.setIsCollected(collectMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeCollect>()
                            .eq(KnowledgeCollect::getKnowledgeId, knowledge.getId())
                            .eq(KnowledgeCollect::getUserId, currentUserId)
            ) > 0);
        }
        
        // 优先从本地缓存取用户信息，避免同一页重复远程调用
        UserInfoDTO cachedUser = null;
        if (knowledge.getUserId() != null && userInfoMap != null) {
            cachedUser = userInfoMap.get(knowledge.getUserId());
        }
        if (cachedUser != null) {
            dto.setUsername(cachedUser.getUsername());
            dto.setAvatar(cachedUser.getAvatar());
            dto.setIsDoctor(cachedUser.getIsDoctor());
        } else if (allowRemoteUserQuery && knowledge.getUserId() != null && userClient != null) {
            try {
                Result<UserInfoDTO> userResult = userClient.getUserInfoById(knowledge.getUserId());
                if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                    dto.setUsername(userResult.getData().getUsername());
                    dto.setAvatar(userResult.getData().getAvatar());
                    dto.setIsDoctor(userResult.getData().getIsDoctor());
                }
            } catch (Exception e) {
                log.error("远程获取用户信息失败, 用户ID: {}", knowledge.getUserId(), e);
            }
        }
        return dto;
    }
    
    /**
     * 转换评论为 DTO
     */
    private KnowledgeCommentDTO convertCommentToDTO(KnowledgeComment comment, Long currentUserId) {
        KnowledgeCommentDTO dto = new KnowledgeCommentDTO();
        BeanUtils.copyProperties(comment, dto);
        dto.setReplies(new ArrayList<>());
        
        // TODO: 查询用户信息
        // 检查是否点赞
        if (currentUserId != null) {
            // TODO: 实现评论点赞功能
            dto.setIsLiked(false);
        }
        
        return dto;
    }

    @Override
    public Page<KnowledgeDTO> getUserPublishedKnowledge(Long userId, Integer page, Integer size, Long currentUserId) {
        if (userId == null) return new Page<>(page, size, 0);
        Page<Knowledge> p = new Page<>(page, size);
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Knowledge::getUserId, userId)
                .eq(Knowledge::getStatus, 1)
                .orderByDesc(Knowledge::getCreateTime);
        Page<Knowledge> result = knowledgeMapper.selectPage(p, wrapper);
        return convertToDTOPage(result, currentUserId);
    }

    @Autowired
    private com.medifood.knowledge.notify.NotificationServiceImpl notificationService;

    @Override
    public Page<KnowledgeDTO> getUserLikedKnowledge(Long userId, Integer page, Integer size, Long currentUserId) {
        log.info("查询用户点赞知识列表, userId: {}, page: {}, size: {}, currentUserId: {}", userId, page, size, currentUserId);
        if (userId == null) return new Page<>(page, size, 0);
        
        // 使用普通 SQL 再次确认点赞记录（防止 MyBatis-Plus 配置干扰）
        List<KnowledgeLike> likes = likeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeLike>()
                        .eq(KnowledgeLike::getUserId, userId)
        );
        log.info("用户 {} 在数据库中的点赞记录数: {}", userId, likes.size());
        
        List<Long> ids = likes.stream()
                .map(KnowledgeLike::getKnowledgeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        log.info("用户 {} 点赞的有效知识 ID 列表: {}", userId, ids);
        
        if (ids.isEmpty()) {
            log.info("用户 {} 点赞列表经过过滤后为空", userId);
            return new Page<>(page, size, 0);
        }
        
        Page<Knowledge> p = new Page<>(page, size);
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Knowledge::getId, ids)
                .eq(Knowledge::getStatus, 1)
                .orderByDesc(Knowledge::getCreateTime);
        
        Page<Knowledge> result = knowledgeMapper.selectPage(p, wrapper);
        log.info("最终查询到的有效知识记录数: {}", result.getRecords().size());
        
        // 打印出查询到的知识标题，方便调试
        result.getRecords().forEach(k -> log.info("点赞列表中的知识: ID={}, Title={}", k.getId(), k.getTitle()));
        
        return convertToDTOPage(result, currentUserId);
    }

    @Override
    public Page<KnowledgeDTO> getUserCollectedKnowledge(Long userId, Integer page, Integer size, Long currentUserId) {
        log.info("查询用户收藏知识列表, userId: {}, page: {}, size: {}, currentUserId: {}", userId, page, size, currentUserId);
        if (userId == null) return new Page<>(page, size, 0);
        List<KnowledgeCollect> collects = collectMapper.selectList(
                new LambdaQueryWrapper<KnowledgeCollect>()
                        .eq(KnowledgeCollect::getUserId, userId)
        );
        log.info("用户 {} 收藏知识记录数: {}", userId, collects.size());
        if (collects.isEmpty()) return new Page<>(page, size, 0);
        List<Long> ids = collects.stream().map(KnowledgeCollect::getKnowledgeId).collect(Collectors.toList());
        log.info("收藏的知识ID列表: {}", ids);
        Page<Knowledge> p = new Page<>(page, size);
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Knowledge::getId, ids).eq(Knowledge::getStatus, 1).orderByDesc(Knowledge::getCreateTime);
        Page<Knowledge> result = knowledgeMapper.selectPage(p, wrapper);
        log.info("最终返回的收藏知识记录数: {}", result.getRecords().size());
        return convertToDTOPage(result, currentUserId);
    }

    private final Object hotCacheLock = new Object();

    @Override
    public List<KnowledgeDTO> getHotKnowledge(Integer size, Long currentUserId) {
        if (size == null || size <= 0) {
            size = HOT_CACHE_SIZE;
        }

        // ① 优先读 Redis 缓存（O(1)，完全不查数据库）
        try {
            String cached = stringRedisTemplate.opsForValue().get(HOT_KNOWLEDGE_CACHE_KEY);
            if (StrUtil.isNotBlank(cached)) {
                List<KnowledgeDTO> list = JSONUtil.toList(cached, KnowledgeDTO.class);
                log.debug("热门知识命中 Redis 缓存，返回 {} 条", list.size());
                return list.subList(0, Math.min(size, list.size()));
            }
        } catch (Exception e) {
            log.warn("读取热门知识 Redis 缓存失败: {}", e.getMessage());
        }

        // ② 缓存未命中，加锁防止缓存击穿（并发风暴）
        synchronized (hotCacheLock) {
            try {
                // 双重检查锁（Double-Checked Locking）
                String cached = stringRedisTemplate.opsForValue().get(HOT_KNOWLEDGE_CACHE_KEY);
                if (StrUtil.isNotBlank(cached)) {
                    List<KnowledgeDTO> list = JSONUtil.toList(cached, KnowledgeDTO.class);
                    log.debug("热门知识命中 Redis 缓存(加锁后)，返回 {} 条", list.size());
                    return list.subList(0, Math.min(size, list.size()));
                }

                // 只有拿到锁的第一个线程去刷新缓存
                log.info("缓存为空，当前线程获取到锁，准备刷新热门知识缓存...");
                refreshHotKnowledgeCache();
                
                cached = stringRedisTemplate.opsForValue().get(HOT_KNOWLEDGE_CACHE_KEY);
                if (StrUtil.isNotBlank(cached)) {
                    List<KnowledgeDTO> list = JSONUtil.toList(cached, KnowledgeDTO.class);
                    return list.subList(0, Math.min(size, list.size()));
                }
            } catch (Exception e) {
                log.warn("加锁刷新热门知识缓存失败，准备降级查库: {}", e.getMessage());
            }

            // ③ 极端情况兜底：直接查库（放在锁内，防止并发压垮数据库和用户服务）
            log.warn("热门知识缓存未命中且刷新失败，降级直接查库");
            LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Knowledge::getStatus, 1)
                    .last("ORDER BY (like_count * 3 + view_count + comment_count * 2) DESC LIMIT " + size);
            List<Knowledge> dbList = knowledgeMapper.selectList(wrapper);
            Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(dbList);
            return dbList.stream().map(k -> convertToDTO(k, null, userInfoMap)).collect(Collectors.toList());
        }
    }

/*    @Override
    public void refreshHotKnowledgeCache() {
        log.info("开始刷新热门知识 Redis 缓存...");
        try {
            // 1. 尝试从 ZSet 获取前 N 名的 ID
            java.util.Set<String> topIdsStr = stringRedisTemplate.opsForZSet().reverseRange(HOT_KNOWLEDGE_ZSET_KEY, 0, HOT_CACHE_SIZE - 1);
            List<Knowledge> list = new ArrayList<>();
            
            if (topIdsStr != null && !topIdsStr.isEmpty()) {
                List<Long> ids = topIdsStr.stream().map(Long::valueOf).collect(Collectors.toList());
                // 使用 selectBatchIds 获取详情，避免全表扫描
                List<Knowledge> dbList = knowledgeMapper.selectBatchIds(ids);
                Map<Long, Knowledge> map = dbList.stream().collect(Collectors.toMap(Knowledge::getId, k -> k));
                // 按照 ZSet 返回的顺序组装
                for (Long id : ids) {
                    Knowledge k = map.get(id);
                    if (k != null && k.getStatus() == 1) {
                        list.add(k);
                    }
                }
                log.info("通过 ZSet 获取了 {} 条热门知识", list.size());
            } else {
                // 如果 ZSet 为空（例如首次启动），降级为全表扫描并初始化 ZSet
                log.info("ZSet 为空，降级全表扫描获取热门知识并初始化 ZSet");
                LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Knowledge::getStatus, 1)
                        .last("ORDER BY (like_count * 3 + view_count + comment_count * 2) DESC LIMIT " + HOT_CACHE_SIZE);
                list = knowledgeMapper.selectList(wrapper);
                
                // 初始化 ZSet
                for (Knowledge k : list) {
                    double score = k.getLikeCount() * 3.0 + k.getViewCount() * 1.0 + k.getCommentCount() * 2.0;
                    stringRedisTemplate.opsForZSet().add(HOT_KNOWLEDGE_ZSET_KEY, k.getId().toString(), score);
                }
            }

            // 缓存时不携带用户个人状态（isLiked / isCollected），首页展示无需此信息
            Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(list);
            List<KnowledgeDTO> dtoList = list.stream()
                    .map(k -> convertToDTO(k, null, userInfoMap))
                    .collect(Collectors.toList());

            String json = JSONUtil.toJsonStr(dtoList);
            stringRedisTemplate.opsForValue().set(
                    HOT_KNOWLEDGE_CACHE_KEY,
                    json,
                    CACHE_TTL_HOURS, TimeUnit.HOURS);

            log.info("热门知识缓存刷新完成，共 {} 条，TTL={}h", dtoList.size(), CACHE_TTL_HOURS);
        } catch (Exception e) {
            log.error("刷新热门知识 Redis 缓存失败: {}", e.getMessage(), e);
        }
    }*/
@Override
public void refreshHotKnowledgeCache() {
    log.info("开始刷新热门知识 Redis 缓存...");
    try {
        // ===================== 核心修改 =====================
        // 1. 先删除旧的 ZSet 缓存（你要求的逻辑）
        stringRedisTemplate.delete(HOT_KNOWLEDGE_ZSET_KEY);
        // ====================================================

        // 2. 直接从数据库查询最新热门数据，重新初始化 ZSet
        log.info("ZSet 已清空，开始从数据库查询最新热门知识并重建排行榜");
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Knowledge::getStatus, 1)
                .last("ORDER BY (like_count * 3 + view_count + comment_count * 2) DESC LIMIT " + HOT_CACHE_SIZE);
        List<Knowledge> list = knowledgeMapper.selectList(wrapper);

        // 重建 ZSet 排行榜
        for (Knowledge k : list) {
            double score = k.getLikeCount() * 3.0 + k.getViewCount() * 1.0 + k.getCommentCount() * 2.0;
            stringRedisTemplate.opsForZSet().add(HOT_KNOWLEDGE_ZSET_KEY, k.getId().toString(), score);
        }

        // 缓存时不携带用户个人状态（isLiked / isCollected），首页展示无需此信息
        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(list);
        List<KnowledgeDTO> dtoList = list.stream()
                .map(k -> convertToDTO(k, null, userInfoMap))
                .collect(Collectors.toList());

        String json = JSONUtil.toJsonStr(dtoList);
        stringRedisTemplate.opsForValue().set(
                HOT_KNOWLEDGE_CACHE_KEY,
                json,
                CACHE_TTL_HOURS, TimeUnit.HOURS);

        log.info("热门知识缓存刷新完成，共 {} 条，TTL={}h", dtoList.size(), CACHE_TTL_HOURS);
    } catch (Exception e) {
        log.error("刷新热门知识 Redis 缓存失败: {}", e.getMessage(), e);
    }
}

    @Override
    public void syncAllToES() {
        log.info("开始同步所有知识到 Elasticsearch...");
        List<Knowledge> knowledgeList = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>().eq(Knowledge::getStatus, 1));

        int syncCount = 0;

        for (Knowledge knowledge : knowledgeList) {
            try {
                esSyncEventPublisher.publishUpdate(
                        knowledge.getId(),
                        EsSyncEvent.EntityType.KNOWLEDGE,
                        "knowledge_index",
                        buildKnowledgeVersion(knowledge),
                        buildKnowledgePayload(knowledge)
                );
                syncCount++;
            } catch (Exception e) {
                log.error("发送知识ES同步消息失败，知识ID: {}", knowledge.getId(), e);
            }
        }
        log.info("知识同步消息发送完成，共计 {} 条，成功发送 {} 条。", knowledgeList.size(), syncCount);
    }

    private Page<KnowledgeDTO> convertToDTOPage(Page<Knowledge> page, Long currentUserId) {
        Map<Long, UserInfoDTO> userInfoMap = buildUserInfoMap(page.getRecords());
        Page<KnowledgeDTO> dtoPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<KnowledgeDTO> dtoList = page.getRecords().stream()
                .map(k -> convertToDTO(k, currentUserId, userInfoMap))
                .collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    /**
     * 批量构建当前页涉及用户信息，避免同一页重复远程调用 user-service。
     */
    private Map<Long, UserInfoDTO> buildUserInfoMap(List<Knowledge> records) {
        if (records == null || records.isEmpty() || userClient == null) {
            return new HashMap<>();
        }
        Map<Long, UserInfoDTO> userInfoMap = new HashMap<>();
        List<Long> userIds = records.stream()
                .map(Knowledge::getUserId)
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

    private Map<String, Object> buildKnowledgePayload(Knowledge knowledge) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", knowledge.getId());
        payload.put("title", knowledge.getTitle());
        payload.put("content", knowledge.getContent());
        payload.put("categoryId", knowledge.getCategoryId());
        payload.put("userId", knowledge.getUserId());
        payload.put("coverImage", knowledge.getCoverImage());
        payload.put("images", parseStringList(knowledge.getImages()));
        payload.put("ingredientNames", ingredientMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeIngredient>()
                                .eq(KnowledgeIngredient::getKnowledgeId, knowledge.getId()))
                .stream()
                .map(KnowledgeIngredient::getIngredientName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList()));
        payload.put("status", knowledge.getStatus());
        payload.put("updateTime", knowledge.getUpdateTime() != null ? knowledge.getUpdateTime().toString() : null);
        return payload;
    }

    private void replaceKnowledgeIngredients(Long knowledgeId, List<String> ingredientNames) {
        ingredientMapper.delete(new LambdaQueryWrapper<KnowledgeIngredient>()
                .eq(KnowledgeIngredient::getKnowledgeId, knowledgeId));
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return;
        }
        for (String ingredientName : ingredientNames) {
            if (StrUtil.isBlank(ingredientName)) {
                continue;
            }
            KnowledgeIngredient ingredient = new KnowledgeIngredient();
            ingredient.setKnowledgeId(knowledgeId);
            ingredient.setIngredientName(ingredientName.trim());
            ingredientMapper.insert(ingredient);
        }
    }

    /**
     * 内部接口：触发知识 ES 重新同步（内容审核通过后由审核服务 Feign 调用）
     */
    @Override
    public void resyncEs(Long knowledgeId) {
        publishKnowledgeSyncEvent(knowledgeId, EsSyncEvent.SyncType.UPDATE);
    }

    private void publishKnowledgeSyncEvent(Long knowledgeId, EsSyncEvent.SyncType syncType) {
        Knowledge savedKnowledge = knowledgeMapper.selectById(knowledgeId);
        if (savedKnowledge == null) {
            return;
        }
        try {
            if (syncType == EsSyncEvent.SyncType.CREATE) {
                esSyncEventPublisher.publishCreate(
                        savedKnowledge.getId(),
                        EsSyncEvent.EntityType.KNOWLEDGE,
                        "knowledge_index",
                        buildKnowledgeVersion(savedKnowledge),
                        buildKnowledgePayload(savedKnowledge)
                );
            } else {
                esSyncEventPublisher.publishUpdate(
                        savedKnowledge.getId(),
                        EsSyncEvent.EntityType.KNOWLEDGE,
                        "knowledge_index",
                        buildKnowledgeVersion(savedKnowledge),
                        buildKnowledgePayload(savedKnowledge)
                );
            }
        } catch (Exception e) {
            log.error("发送知识ES同步消息失败，知识ID: {}", knowledgeId, e);
        }
    }

    private Long buildKnowledgeVersion(Knowledge knowledge) {
        if (knowledge == null) {
            return System.currentTimeMillis();
        }
        if (knowledge.getUpdateTime() != null) {
            return knowledge.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (knowledge.getCreateTime() != null) {
            return knowledge.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return knowledge.getId();
    }

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(json, String.class);
    }


    private KnowledgeDTO convertToDTO(Knowledge knowledge, Long currentUserId, Map<Long, UserInfoDTO> userInfoMap) {
        return convertToDTO(knowledge, currentUserId, userInfoMap, false);
    }
}
