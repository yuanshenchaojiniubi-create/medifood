package com.medifood.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.recipe.dto.RecipeCommentDTO;
import com.medifood.recipe.dto.RecipeDTO;
import com.medifood.recipe.dto.RecipePublishDTO;
import com.medifood.recipe.dto.RecipeQueryDTO;
import com.medifood.recipe.entity.RecipeTag;

import java.util.List;

/**
 * 食谱服务接口
 * 
 * @author MediFood Team
 */
public interface RecipeService {
    
    /**
     * 发布食谱
     */
    Long publish(Long userId, RecipePublishDTO publishDTO);

    /**
     * 内部接口：触发食谱 ES 重新同步（内容审核通过后由审核服务调用）
     */
    void resyncEs(Long recipeId);

    /**
     * 更新食谱
     */
    Long updateRecipe(Long recipeId, Long userId, RecipePublishDTO publishDTO);
    
    /**
     * 获取食谱列表
     */
    Page<RecipeDTO> getRecipeList(RecipeQueryDTO queryDTO, Long currentUserId);
    
    /**
     * 搜索食谱
     */
    Page<RecipeDTO> searchRecipe(String keyword, Integer page, Integer size, Long currentUserId);
    
    /**
     * 获取食谱详情
     */
    RecipeDTO getRecipeDetail(Long id, Long currentUserId);
    
    /**
     * 点赞食谱
     */
    void likeRecipe(Long recipeId, Long userId);
    
    /**
     * 取消点赞
     */
    void unlikeRecipe(Long recipeId, Long userId);
    
    /**
     * 收藏食谱
     */
    void collectRecipe(Long recipeId, Long userId);
    
    /**
     * 取消收藏
     */
    void uncollectRecipe(Long recipeId, Long userId);
    
    /**
     * 评论食谱
     */
    void commentRecipe(Long userId, Long recipeId, String content, Long parentId);
    
    /**
     * 删除食谱
     */
    void deleteRecipe(Long recipeId, Long userId);

    /**
     * 获取评论列表
     */
    Page<RecipeCommentDTO> getCommentList(Long recipeId, Integer page, Integer size, Long currentUserId);

    /**
     * 删除评论
     */
    void deleteComment(Long commentId, Long userId);

    /**
     * 获取所有食谱标签
     */
    List<RecipeTag> getAllTags();

    /**
     * 创建新标签
     */
    RecipeTag createTag(String tagName, String tagType);

    /**
     * 获取用户发布的食谱
     */
    Page<RecipeDTO> getUserPublishedRecipes(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取用户点赞的食谱
     */
    Page<RecipeDTO> getUserLikedRecipes(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取用户收藏的食谱
     */
    Page<RecipeDTO> getUserCollectedRecipes(Long userId, Integer page, Integer size, Long currentUserId);

    /**
     * 获取热门食谱列表（优先读 Redis 缓存，缓存未命中时降级查库）
     *
     * @param size          返回条数
     * @param currentUserId 当前用户 ID（可为 null）
     * @return 热门食谱列表
     */
    List<RecipeDTO> getHotRecipes(Integer size, Long currentUserId);

    /**
     * 刷新热门食谱 Redis 缓存（由定时任务调用，也在服务启动时预热）
     * 使用加权算法：热度分 = 点赞数×3 + 浏览数×1 + 评论数×2
     */
    void refreshHotRecipeCache();

    /**
     * 同步所有食谱到 Elasticsearch
     */
    void syncAllToES();

    /**
     * 根据节气名称精确查询食谱（优先使用 suitable_seasons 字段匹配）
     * <p>
     * 与 searchRecipe 不同的是，此方法使用 FIND_IN_SET 精确匹配节气标签，
     * 匹配精度更高，适用于首页节气饮食推荐。
     * </p>
     *
     * @param seasonName   节气名称（如 "立春"、"春分"）
     * @param page         页码
     * @param size         每页条数
     * @param currentUserId 当前用户ID
     * @return 匹配的食谱分页
     */
    Page<RecipeDTO> searchBySeason(String seasonName, Integer page, Integer size, Long currentUserId);

    /**
     * 获取个性化推荐食谱
     * <p>
     * 多层推荐策略：
     * <ol>
     *   <li>内容推荐：基于用户历史行为（点赞/收藏）的标签相似度推荐</li>
     *   <li>协同过滤：找到相似用户，推荐他们喜欢的食谱</li>
     *   <li>热门兜底：新用户返回全局热门食谱</li>
     * </ol>
     * </p>
     *
     * @param userId        当前用户ID（null 时返回热门食谱）
     * @param size          推荐数量
     * @param currentUserId 用于判断 isLiked/isCollected
     * @return 推荐食谱列表
     */
    List<RecipeDTO> recommend(Long userId, Integer size, Long currentUserId);
}

