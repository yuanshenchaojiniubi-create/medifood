package com.medifood.recipe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.recipe.dto.RecipeDTO;
import com.medifood.recipe.dto.RecipePublishDTO;
import com.medifood.recipe.dto.RecipeQueryDTO;
import com.medifood.recipe.dto.SeasonalDietDTO;
import com.medifood.recipe.entity.RecipeTag;
import com.medifood.recipe.service.RecipeService;
import com.medifood.recipe.service.SeasonalDietService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 食谱控制器
 * 
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/api/recipe")
public class RecipeController {
    
    @Autowired
    private RecipeService recipeService;

    @Autowired
    private SeasonalDietService seasonalDietService;
    
    /**
     * 获取热门食谱列表（首页使用，无需登录）
     *
     * @param size          返回条数，默认 6
     * @param currentUserId 当前登录用户 ID（可为 null）
     */
    @GetMapping("/hot")
    public Result<List<RecipeDTO>> getHotRecipes(
            @RequestParam(defaultValue = "6") Integer size,
            @CurrentUser(required = false) Long currentUserId) {
        List<RecipeDTO> list = recipeService.getHotRecipes(size, currentUserId);
        return Result.success(list);
    }

    /**
     * 获取个性化推荐食谱
     * <p>
     * 已登录用户：基于点赞/收藏历史的标签相似度推荐 + 协同过滤
     * 未登录用户：返回热门食谱
     * </p>
     *
     * @param size          返回条数，默认 10
     * @param currentUserId 当前登录用户 ID（可为 null）
     */
    @GetMapping("/recommend")
    public Result<List<RecipeDTO>> recommend(
            @RequestParam(defaultValue = "10") Integer size,
            @CurrentUser(required = false) Long currentUserId) {
        List<RecipeDTO> list = recipeService.recommend(currentUserId, size, currentUserId);
        return Result.success(list);
    }

    @GetMapping("/seasonal-diet")
    public Result<SeasonalDietDTO> getSeasonalDiet(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "false") boolean useDefault) {
        log.info("🍽 [Recipe] 收到节气饮食请求: lat={}, lng={}, useDefault={}", lat, lng, useDefault);
        SeasonalDietDTO diet = seasonalDietService.getSeasonalDiet(lat, lng, useDefault);
        log.info("🍽 [Recipe] 节气饮食返回: term={}, city={}, weather={}, source={}",
                diet.getSourceName(),
                diet.getWeather() != null ? diet.getWeather().getCity() : "null",
                diet.getWeather() != null ? diet.getWeather().getCondition() : "null",
                diet.getWeather() != null ? diet.getWeather().getSource() : "null");
        return Result.success(diet);
    }

    @PostMapping("/publish")
    public Result<Long> publish(@CurrentUser Long userId,
                                @Validated @RequestBody RecipePublishDTO publishDTO) {
        Long recipeId = recipeService.publish(userId, publishDTO);
        return Result.success("发布成功，正在审核中，审核通过后将自动公开", recipeId);
    }

    /**
     * 内部接口：触发食谱 ES 重新同步（内容审核通过后由审核服务 Feign 调用，仅服务间使用）
     */
    @PostMapping("/internal/es-resync/{id}")
    public Result<Void> resyncEs(@PathVariable("id") Long id) {
        recipeService.resyncEs(id);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<Long> updateRecipe(@PathVariable("id") Long id,
                                     @CurrentUser Long userId,
                                     @Validated @RequestBody RecipePublishDTO publishDTO) {
        Long recipeId = recipeService.updateRecipe(id, userId, publishDTO);
        return Result.success("更新成功", recipeId);
    }
    
    @GetMapping("/list")
    public Result<Page<RecipeDTO>> getRecipeList(@Validated RecipeQueryDTO queryDTO,
                                                  @CurrentUser(required = false) Long currentUserId) {
        Page<RecipeDTO> page = recipeService.getRecipeList(queryDTO, currentUserId);
        return Result.success(page);
    }
    
    @GetMapping("/search")
    public Result<Page<RecipeDTO>> searchRecipe(@RequestParam String keyword,
                                                 @RequestParam(defaultValue = "1",name = "page") Integer page,
                                                 @RequestParam(defaultValue = "10",name = "size") Integer size,
                                                 @CurrentUser(required = false) Long currentUserId) {
        Page<RecipeDTO> result = recipeService.searchRecipe(keyword, page, size, currentUserId);
        return Result.success(result);
    }
    
    @GetMapping("/{id}")
    public Result<RecipeDTO> getRecipeDetail(@PathVariable("id") Long id,
                                              @CurrentUser(required = false) Long currentUserId) {
        RecipeDTO recipe = recipeService.getRecipeDetail(id, currentUserId);
        return Result.success(recipe);
    }
    
    @PostMapping("/{id}/like")
    public Result<?> likeRecipe(@PathVariable("id") Long id, @CurrentUser Long userId) {
        recipeService.likeRecipe(id, userId);
        return Result.success("点赞成功");
    }
    
    @DeleteMapping("/{id}/like")
    public Result<?> unlikeRecipe(@PathVariable("id") Long id, @CurrentUser Long userId) {
        recipeService.unlikeRecipe(id, userId);
        return Result.success("取消点赞成功");
    }
    
    @PostMapping("/{id}/collect")
    public Result<?> collectRecipe(@PathVariable("id") Long id, @CurrentUser Long userId) {
        recipeService.collectRecipe(id, userId);
        return Result.success("收藏成功");
    }
    
    @DeleteMapping("/{id}/collect")
    public Result<?> uncollectRecipe(@PathVariable("id") Long id, @CurrentUser Long userId) {
        recipeService.uncollectRecipe(id, userId);
        return Result.success("取消收藏成功");
    }
    
    @PostMapping("/{id}/comment")
    public Result<?> commentRecipe(@PathVariable("id") Long id,
                                   @CurrentUser Long userId,
                                   @RequestParam(name = "content") String content,
                                   @RequestParam(required = false, name = "parentId") Long parentId) {
        recipeService.commentRecipe(userId, id, content, parentId);
        return Result.success("评论成功");
    }
    
    @DeleteMapping("/{id}")
    public Result<?> deleteRecipe(@PathVariable("id") Long id, @CurrentUser Long userId) {
        recipeService.deleteRecipe(id, userId);
        return Result.success("删除成功");
    }

    /**
     * 获取用户发布的食谱
     */
    @GetMapping("/user/{userId}/published")
    public Result<Page<RecipeDTO>> getUserPublishedRecipes(@PathVariable("userId") Long userId,
                                                         @RequestParam(defaultValue = "1") Integer page,
                                                         @RequestParam(defaultValue = "10") Integer size,
                                                         @CurrentUser(required = false) Long currentUserId) {
        Page<RecipeDTO> res = recipeService.getUserPublishedRecipes(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 获取用户点赞的食谱
     */
    @GetMapping("/user/{userId}/liked")
    public Result<Page<RecipeDTO>> getUserLikedRecipes(@PathVariable("userId") Long userId,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer size,
                                                      @CurrentUser(required = false) Long currentUserId) {
        Page<RecipeDTO> res = recipeService.getUserLikedRecipes(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 获取用户收藏的食谱
     */
    @GetMapping("/user/{userId}/collected")
    public Result<Page<RecipeDTO>> getUserCollectedRecipes(@PathVariable("userId") Long userId,
                                                         @RequestParam(defaultValue = "1") Integer page,
                                                         @RequestParam(defaultValue = "10") Integer size,
                                                         @CurrentUser(required = false) Long currentUserId) {
        Page<RecipeDTO> res = recipeService.getUserCollectedRecipes(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 同步所有食谱到 ES
     */
    @PostMapping("/sync")
    public Result<?> syncAllToES() {
        recipeService.syncAllToES();
        return Result.success("同步任务已触发");
    }

    /**
     * 获取所有食谱标签
     */
    @GetMapping("/tags")
    public Result<List<RecipeTag>> getAllTags() {
        List<RecipeTag> tags = recipeService.getAllTags();
        return Result.success(tags);
    }

    /**
     * 创建新标签
     */
    @PostMapping("/tags")
    public Result<RecipeTag> createTag(@RequestBody Map<String, String> body) {
        String tagName = body.get("tagName");
        String tagType = body.getOrDefault("tagType", "custom");
        RecipeTag tag = recipeService.createTag(tagName, tagType);
        return Result.success("创建成功", tag);
    }
}
