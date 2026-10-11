package com.medifood.qa.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.qa.dto.AnswerDTO;
import com.medifood.qa.dto.AnswerPublishDTO;
import com.medifood.qa.dto.QuestionDTO;
import com.medifood.qa.dto.QuestionPublishDTO;
import com.medifood.qa.service.QaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/qa")
public class QaController {
    
    @Autowired
    private QaService qaService;
    
    @PostMapping("/question")
    public Result<Long> publishQuestion(@CurrentUser Long userId,
                                        @Validated @RequestBody QuestionPublishDTO publishDTO) {
        Long questionId = qaService.publishQuestion(userId, publishDTO);
        return Result.success("发布成功", questionId);
    }
    
    @GetMapping("/question/list")
    public Result<Page<QuestionDTO>> getQuestionList(@RequestParam(required = false,name = "categoryId") String categoryId,
                                                      @RequestParam(defaultValue = "1",name = "page") Integer page,
                                                      @RequestParam(defaultValue = "10",name = "size") Integer size,
                                                      @CurrentUser(required = false) Long currentUserId) {
        Page<QuestionDTO> questionPage = qaService.getQuestionList(categoryId, page, size, currentUserId);
        return Result.success(questionPage);
    }
    
    @GetMapping("/question/search")
    public Result<Page<QuestionDTO>> searchQuestion(@RequestParam String keyword,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @CurrentUser(required = false) Long currentUserId) {
        Page<QuestionDTO> questionPage = qaService.searchQuestion(keyword, page, size, currentUserId);
        return Result.success(questionPage);
    }
    
    @GetMapping("/question/{id}")
    public Result<QuestionDTO> getQuestionDetail(@PathVariable Long id,
                                                  @CurrentUser(required = false) Long currentUserId) {
        QuestionDTO question = qaService.getQuestionDetail(id, currentUserId);
        return Result.success(question);
    }
    
    @PostMapping("/question/{id}/like")
    public Result<?> likeQuestion(@PathVariable Long id, @CurrentUser Long userId) {
        qaService.likeQuestion(id, userId);
        return Result.success("点赞成功");
    }
    
    @DeleteMapping("/question/{id}/like")
    public Result<?> unlikeQuestion(@PathVariable Long id, @CurrentUser Long userId) {
        qaService.unlikeQuestion(id, userId);
        return Result.success("取消点赞成功");
    }
    
    @PostMapping("/answer")
    public Result<Long> publishAnswer(@CurrentUser Long userId,
                                     @Validated @RequestBody AnswerPublishDTO publishDTO) {
        Long answerId = qaService.publishAnswer(userId, publishDTO);
        return Result.success("回答成功", answerId);
    }
    
    @GetMapping("/question/{id}/answers")
    public Result<Page<AnswerDTO>> getAnswerList(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @CurrentUser(required = false) Long currentUserId) {
        Page<AnswerDTO> answerPage = qaService.getAnswerList(id, page, size, currentUserId);
        return Result.success(answerPage);
    }
    
    @PostMapping("/answer/{id}/like")
    public Result<?> likeAnswer(@PathVariable Long id, @CurrentUser Long userId) {
        qaService.likeAnswer(id, userId);
        return Result.success("点赞成功");
    }
    
    @DeleteMapping("/answer/{id}/like")
    public Result<?> unlikeAnswer(@PathVariable Long id, @CurrentUser Long userId) {
        qaService.unlikeAnswer(id, userId);
        return Result.success("取消点赞成功");
    }
    
    @PostMapping("/answer/{id}/best")
    public Result<?> setBestAnswer(@PathVariable Long id,
                                  @RequestParam Long questionId,
                                  @CurrentUser Long userId) {
        qaService.setBestAnswer(questionId, id, userId);
        return Result.success("设置最佳答案成功");
    }

    /**
     * 获取用户发布的问题
     */
    @GetMapping("/user/{userId}/questions")
    public Result<Page<QuestionDTO>> getUserQuestions(@PathVariable Long userId,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer size,
                                                       @CurrentUser(required = false) Long currentUserId) {
        Page<QuestionDTO> res = qaService.getUserQuestions(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 获取用户点赞的问题
     */
    @GetMapping("/user/{userId}/liked")
    public Result<Page<QuestionDTO>> getUserLikedQuestions(@PathVariable Long userId,
                                                            @RequestParam(defaultValue = "1") Integer page,
                                                            @RequestParam(defaultValue = "10") Integer size,
                                                            @CurrentUser(required = false) Long currentUserId) {
        Page<QuestionDTO> res = qaService.getUserLikedQuestions(userId, page, size, currentUserId);
        return Result.success(res);
    }

    /**
     * 删除问题
     */
    @DeleteMapping("/question/{id}")
    public Result<?> deleteQuestion(@PathVariable Long id, @CurrentUser Long userId) {
        qaService.deleteQuestion(id, userId);
        return Result.success("删除成功");
    }
}

