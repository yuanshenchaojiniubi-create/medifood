package com.medifood.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.knowledge.dto.*;
import com.medifood.knowledge.service.KnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识控制器
 * 
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {
    
    @Autowired
    private KnowledgeService knowledgeService;
    
    /**
     * 获取热门知识列表（首页使用，无需登录）
     *
     * @param size          返回条数，默认 6
     * @param currentUserId 当前登录用户 ID（可为 null）
     */
    @GetMapping("/hot")
    public Result<List<KnowledgeDTO>> getHotKnowledge(
            @RequestParam(defaultValue = "6") Integer size,
            @CurrentUser(required = false) Long currentUserId) {
        List<KnowledgeDTO> list = knowledgeService.getHotKnowledge(size, currentUserId);
        return Result.success(list);
    }

    /**
     * 发布知识
     */
    @PostMapping("/publish")
    public Result<Long> publish(@CurrentUser Long userId,
                                @Validated @RequestBody KnowledgePublishDTO publishDTO) {
        //Long userId = Long.parseLong(request.getHeader("X-User-Id"));
        Long knowledgeId = knowledgeService.publish(userId, publishDTO);
        return Result.success("发布成功，正在审核中，审核通过后将自动公开", knowledgeId);
    }

    /**
     * 内部接口：触发知识 ES 重新同步（内容审核通过后由审核服务 Feign 调用，仅服务间使用）
     */
    @PostMapping("/internal/es-resync/{id}")
    public Result<Void> resyncEs(@PathVariable("id") Long id) {
        knowledgeService.resyncEs(id);
        return Result.success(null);
    }

    /**
     * 更新知识
     */
    @PutMapping("/{id}")
    public Result<Long> updateKnowledge(@PathVariable("id") Long id,
                                        @CurrentUser Long userId,
                                        @Validated @RequestBody KnowledgePublishDTO publishDTO) {
        Long knowledgeId = knowledgeService.updateKnowledge(id, userId, publishDTO);
        return Result.success("更新成功", knowledgeId);
    }
    
    /**
     * 获取用户知识列表
     */
    @GetMapping("/list")
    public Result<Page<KnowledgeDTO>> getKnowledgeList(@Validated KnowledgeQueryDTO queryDTO,
                                                       @CurrentUser Long currentUserId) {
        //Long currentUserId = Long.parseLong(request.getHeader("X-User-Id"));
        Page<KnowledgeDTO> page = knowledgeService.getKnowledgeList(queryDTO, currentUserId);
        System.out.println("page:"+page.getTotal());
        return Result.success(page);
    }
    
    /**
     * 搜索知识
     */
    @GetMapping("/search")
    public Result<Page<KnowledgeDTO>> searchKnowledge(@RequestParam String keyword,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer size,
                                                      HttpServletRequest  request) {
        System.out.println("request获取："+request.getHeader("X-User-Id"));
        Long currentUserId = Long.parseLong(request.getHeader("X-User-Id"));
        Page<KnowledgeDTO> result = knowledgeService.searchKnowledge(keyword, page, size, currentUserId);
        return Result.success(result);
    }
    
    /**
     * 获取知识详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeDTO> getKnowledgeDetail(@PathVariable("id") Long id,
                                                   HttpServletRequest  request) {
        System.out.println("知识id:"+id);
        System.out.println("request获取："+request.getHeader("X-User-Id"));
        Long currentUserId = Long.parseLong(request.getHeader("X-User-Id"));
        KnowledgeDTO knowledge = knowledgeService.getKnowledgeDetail(id, currentUserId);
        return Result.success(knowledge);
    }
    
    /**
     * 点赞知识
     */
    @PostMapping("/{id}/like")
    public Result<?> likeKnowledge(@PathVariable("id") Long id, HttpServletRequest  request) {
        Long userId = Long.parseLong(request.getHeader("X-User-Id"));
        knowledgeService.likeKnowledge(id, userId);
        return Result.success("点赞成功");
    }
    
    /**
     * 取消点赞
     */
    @DeleteMapping("/{id}/like")
    public Result<?> unlikeKnowledge(@PathVariable("id") Long id, HttpServletRequest  request) {
        Long userId = Long.parseLong(request.getHeader("X-User-Id"));
        knowledgeService.unlikeKnowledge(id, userId);
        return Result.success("取消点赞成功");
    }
    
    /**
     * 收藏知识
     */
    @PostMapping("/{id}/collect")
    public Result<?> collectKnowledge(@PathVariable("id") Long id, @CurrentUser Long userId) {
        knowledgeService.collectKnowledge(id, userId);
        return Result.success("收藏成功");
    }
    
    /**
     * 取消收藏
     */
    @DeleteMapping("/{id}/collect")
    public Result<?> uncollectKnowledge(@PathVariable("id") Long id, @CurrentUser Long userId) {
        knowledgeService.uncollectKnowledge(id, userId);
        return Result.success("取消收藏成功");
    }
    
    /**
     * 评论知识
     */
    @PostMapping("/comment")
    public Result<KnowledgeCommentDTO> commentKnowledge(@CurrentUser Long userId,
                                                         @Validated @RequestBody KnowledgeCommentCreateDTO commentDTO) {
        System.out.println("到达了评论这里接口："+commentDTO.toString());
        KnowledgeCommentDTO comment = knowledgeService.commentKnowledge(userId, commentDTO);
        return Result.success("评论成功", comment);
    }
    
    /**
     * 获取评论列表
     */
    @GetMapping("/{id}/comments")
    public Result<Page<KnowledgeCommentDTO>> getCommentList(@PathVariable("id") Long id,
                                                             @RequestParam(defaultValue = "1",name = "page") Integer page,
                                                             @RequestParam(defaultValue = "10",name = "size") Integer size,
                                                             @CurrentUser(required = false) Long currentUserId) {
        Page<KnowledgeCommentDTO> commentPage = knowledgeService.getCommentList(id, page, size, currentUserId);
        return Result.success(commentPage);
    }
    
    /**
     * 删除知识
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteKnowledge(@PathVariable("id") Long id, @CurrentUser Long userId) {
        knowledgeService.deleteKnowledge(id, userId);
        return Result.success("删除成功");
    }

    /**
     * 获取用户发布的知识
     */
    @GetMapping("/user/{userId}/published")
    public Result<Page<KnowledgeDTO>> getUserPublishedKnowledge(@PathVariable("userId") Long userId,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "10") Integer size,
                                                               @CurrentUser(required = false) Long currentUserId) {
        Page<KnowledgeDTO> res = knowledgeService.getUserPublishedKnowledge(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 获取用户点赞的知识
     */
    @GetMapping("/user/{userId}/liked")
    public Result<Page<KnowledgeDTO>> getUserLikedKnowledge(@PathVariable("userId") Long userId,
                                                           @RequestParam(defaultValue = "1") Integer page,
                                                           @RequestParam(defaultValue = "10") Integer size,
                                                           @CurrentUser(required = false) Long currentUserId) {
        Page<KnowledgeDTO> res = knowledgeService.getUserLikedKnowledge(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 获取用户收藏的知识
     */
    @GetMapping("/user/{userId}/collected")
    public Result<Page<KnowledgeDTO>> getUserCollectedKnowledge(@PathVariable("userId") Long userId,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "10") Integer size,
                                                               @CurrentUser(required = false) Long currentUserId) {
        Page<KnowledgeDTO> res = knowledgeService.getUserCollectedKnowledge(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 同步所有知识到 ES
     */
    @PostMapping("/sync")
    public Result<?> syncAllToES() {
        knowledgeService.syncAllToES();
        return Result.success("同步任务已触发");
    }
}
