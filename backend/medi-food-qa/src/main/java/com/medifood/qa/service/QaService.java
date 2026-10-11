package com.medifood.qa.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.qa.dto.AnswerDTO;
import com.medifood.qa.dto.AnswerPublishDTO;
import com.medifood.qa.dto.QuestionDTO;
import com.medifood.qa.dto.QuestionPublishDTO;

public interface QaService {
    
    Long publishQuestion(Long userId, QuestionPublishDTO publishDTO);
    
    Page<QuestionDTO> getQuestionList(String categoryId, Integer page, Integer size, Long currentUserId);
    
    Page<QuestionDTO> searchQuestion(String keyword, Integer page, Integer size, Long currentUserId);
    
    QuestionDTO getQuestionDetail(Long id, Long currentUserId);
    
    void likeQuestion(Long questionId, Long userId);
    
    void unlikeQuestion(Long questionId, Long userId);
    
    Long publishAnswer(Long userId, AnswerPublishDTO publishDTO);
    
    Page<AnswerDTO> getAnswerList(Long questionId, Integer page, Integer size, Long currentUserId);
    
    void likeAnswer(Long answerId, Long userId);
    
    void unlikeAnswer(Long answerId, Long userId);
    
    void setBestAnswer(Long questionId, Long answerId, Long userId);

    Page<QuestionDTO> getUserQuestions(Long userId, Integer page, Integer size, Long currentUserId);

    Page<QuestionDTO> getUserLikedQuestions(Long userId, Integer page, Integer size, Long currentUserId);

    void deleteQuestion(Long questionId, Long userId);
}

