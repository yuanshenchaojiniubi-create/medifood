package com.medifood.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.knowledge.dto.*;

import java.util.List;

/**
 * 知识服务接口
 * 
 * @author MediFood Team
 */
public interface KnowledgeService {
    
    /**
     * 发布知识
     */
    Long publish(Long userId, KnowledgePublishDTO publishDTO);

    /**
     * 内部接口：触发知识 ES 重新同步（内容审核通过后由审核服务调用）
     */
    void resyncEs(Long knowledgeId);

    /**
     * 更新知识
     */
    Long updateKnowledge(Long knowledgeId, Long userId, KnowledgePublishDTO publishDTO);
    
    /**
     * 获取知识列表
     */
    Page<KnowledgeDTO> getKnowledgeList(KnowledgeQueryDTO queryDTO, Long currentUserId);
    
    /**
     * 搜索知识
     */
    Page<KnowledgeDTO> searchKnowledge(String keyword, Integer page, Integer size, Long currentUserId);
    
    /**
     * 获取知识详情
     */
    KnowledgeDTO getKnowledgeDetail(Long id, Long currentUserId);
    
    /**
     * 点赞知识
     */
    void likeKnowledge(Long knowledgeId, Long userId);
    
    /**
     * 取消点赞
     */
    void unlikeKnowledge(Long knowledgeId, Long userId);
    
    /**
     * 收藏知识
     */
    void collectKnowledge(Long knowledgeId, Long userId);
    
    /**
     * 取消收藏
     */
    void uncollectKnowledge(Long knowledgeId, Long userId);
    
    /**
     * 评论知识
     */
    KnowledgeCommentDTO commentKnowledge(Long userId, KnowledgeCommentCreateDTO commentDTO);
    
    /**
     * 获取评论列表
     */
    Page<KnowledgeCommentDTO> getCommentList(Long knowledgeId, Integer page, Integer size, Long currentUserId);
    
    /**
     * 删除知识
     */
    void deleteKnowledge(Long knowledgeId, Long userId);

    /**
     * 获取用户发布的知识
     */
    Page<KnowledgeDTO> getUserPublishedKnowledge(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取用户点赞的知识
     */
    Page<KnowledgeDTO> getUserLikedKnowledge(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取用户收藏的知识
     */
    Page<KnowledgeDTO> getUserCollectedKnowledge(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取热门知识列表（优先读 Redis 缓存，缓存未命中时降级查库）
     *
     * @param size          返回条数
     * @param currentUserId 当前用户 ID（可为 null）
     * @return 热门知识列表
     */
    List<KnowledgeDTO> getHotKnowledge(Integer size, Long currentUserId);

    /**
     * 刷新热门知识 Redis 缓存（由定时任务调用，也在服务启动时预热）
     * 使用加权算法：热度分 = 点赞数×3 + 浏览数×1 + 评论数×2
     */
    void refreshHotKnowledgeCache();

    /**
     * 同步所有知识到 Elasticsearch
     */
    void syncAllToES();
}
