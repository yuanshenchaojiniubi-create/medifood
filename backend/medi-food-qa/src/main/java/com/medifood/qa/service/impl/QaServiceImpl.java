package com.medifood.qa.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.audit.ContentAuditEvent;
import com.medifood.common.audit.ContentAuditEventPublisher;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.ResultCode;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifood.common.util.SensitiveWordFilter;
import com.medifood.qa.dto.AnswerDTO;
import com.medifood.qa.dto.AnswerPublishDTO;
import com.medifood.qa.dto.QuestionDTO;
import com.medifood.qa.dto.QuestionPublishDTO;
import com.medifood.qa.entity.*;
import com.medifood.qa.mapper.*;
import com.medifood.qa.service.QaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QaServiceImpl implements QaService {
    
    @Autowired
    private QaQuestionMapper questionMapper;
    
    @Autowired
    private QaAnswerMapper answerMapper;
    
    @Autowired
    private QaCategoryMapper categoryMapper;
    
    @Autowired
    private QaQuestionLikeMapper questionLikeMapper;
    
    @Autowired
    private QaAnswerLikeMapper answerLikeMapper;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private ContentAuditEventPublisher auditEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishQuestion(Long userId, QuestionPublishDTO publishDTO) {
        // 第一层防线：敏感词同步拦截
        SensitiveWordFilter.check(publishDTO.getTitle(), "问题标题");
        SensitiveWordFilter.check(publishDTO.getContent(), "问题内容");

        QaQuestion question = new QaQuestion();
        question.setUserId(userId);
        question.setTitle(publishDTO.getTitle());
        question.setContent(publishDTO.getContent());
        question.setCategoryId(publishDTO.getCategoryId());
        
        if (publishDTO.getImages() != null && !publishDTO.getImages().isEmpty()) {
            question.setImages(JSONUtil.toJsonStr(publishDTO.getImages()));
        }
        
        question.setViewCount(0);
        question.setLikeCount(0);
        question.setAnswerCount(0);
        question.setStatus(1);
        
        questionMapper.insert(question);

        // 同步到 Elasticsearch
        try {
            java.util.Map<String, Object> source = new java.util.HashMap<>();
            source.put("id", question.getId().toString());
            source.put("title", question.getTitle());
            source.put("content", question.getContent());
            elasticsearchUtil.indexDocument("qa_question_index", question.getId().toString(), source);
        } catch (Exception e) {
            log.error("同步问题到ES失败，问题ID: {}", question.getId(), e);
        }

        log.info("发布问题成功，问题ID: {}, 用户ID: {}", question.getId(), userId);

        // 第二层防线：AI 异步审核（先发后审）
        auditEventPublisher.publish(new ContentAuditEvent(
                question.getId(), userId, ContentAuditEvent.TYPE_QUESTION,
                question.getTitle(), question.getContent(), publishDTO.getImages()));

        return question.getId();
    }
    
    @Override
    public Page<QuestionDTO> getQuestionList(String categoryId, Integer page, Integer size, Long currentUserId) {
        Page<QaQuestion> questionPage = new Page<>(page, size);
        LambdaQueryWrapper<QaQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaQuestion::getStatus, 1);
        
        if (categoryId != null) {
            wrapper.eq(QaQuestion::getCategoryId, categoryId);
        }
        
        wrapper.orderByDesc(QaQuestion::getCreateTime);
        
        Page<QaQuestion> result = questionMapper.selectPage(questionPage, wrapper);
        return convertQuestionToDTOPage(result, currentUserId);
    }
    
    @Override
    public Page<QuestionDTO> searchQuestion(String keyword, Integer page, Integer size, Long currentUserId) {
        if (StrUtil.isBlank(keyword)) {
            return getQuestionList(null, page, size, currentUserId);
        }

        int from = (page - 1) * size;
        List<java.util.Map<String, Object>> searchResults = elasticsearchUtil.search("qa_question_index", keyword, from, size, "title", "content");

        if (searchResults.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        List<Long> ids = searchResults.stream()
                .map(m -> Long.valueOf(m.get("id").toString()))
                .collect(Collectors.toList());

        List<QaQuestion> questions = questionMapper.selectBatchIds(ids);
        java.util.Map<Long, QaQuestion> questionMap = questions.stream().collect(Collectors.toMap(QaQuestion::getId, q -> q));
        
        List<QaQuestion> sortedQuestions = ids.stream()
                .map(questionMap::get)
                .filter(q -> q != null && q.getStatus() == 1)
                .collect(Collectors.toList());

        Page<QaQuestion> questionPage = new Page<>(page, size, sortedQuestions.size());
        questionPage.setRecords(sortedQuestions);
        
        return convertQuestionToDTOPage(questionPage, currentUserId);
    }
    
    @Override
    public QuestionDTO getQuestionDetail(Long id, Long currentUserId) {
        QaQuestion question = questionMapper.selectById(id);
        if (question == null || question.getStatus() != 1) {
            throw new BusinessException(ResultCode.QUESTION_NOT_EXIST);
        }
        
        questionMapper.incrementViewCount(id);
        return convertQuestionToDTO(question, currentUserId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeQuestion(Long questionId, Long userId) {
        LambdaQueryWrapper<QaQuestionLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaQuestionLike::getQuestionId, questionId)
                .eq(QaQuestionLike::getUserId, userId);
        
        if (questionLikeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经点赞过了");
        }
        
        QaQuestionLike like = new QaQuestionLike();
        like.setQuestionId(questionId);
        like.setUserId(userId);
        questionLikeMapper.insert(like);
        
        questionMapper.incrementLikeCount(questionId);
        try {
            QaQuestion q = questionMapper.selectById(questionId);
            if (q != null && q.getUserId() != null && !q.getUserId().equals(userId)) {
                com.medifood.qa.notify.Notification n = new com.medifood.qa.notify.Notification();
                n.setRecipientId(q.getUserId());
                n.setActorId(userId);
                n.setType(1);
                n.setEntityType(4);
                n.setEntityId(questionId);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeQuestion(Long questionId, Long userId) {
        LambdaQueryWrapper<QaQuestionLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaQuestionLike::getQuestionId, questionId)
                .eq(QaQuestionLike::getUserId, userId);
        
        QaQuestionLike like = questionLikeMapper.selectOne(wrapper);
        if (like == null) {
            throw new BusinessException("还未点赞");
        }
        
        questionLikeMapper.deleteById(like.getId());
        questionMapper.decrementLikeCount(questionId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishAnswer(Long userId, AnswerPublishDTO publishDTO) {
        // 第一层防线：敏感词同步拦截
        SensitiveWordFilter.check(publishDTO.getContent(), "回答内容");

        QaAnswer answer = new QaAnswer();
        answer.setQuestionId(publishDTO.getQuestionId());
        answer.setUserId(userId);
        answer.setContent(publishDTO.getContent());
        
        if (publishDTO.getImages() != null && !publishDTO.getImages().isEmpty()) {
            answer.setImages(JSONUtil.toJsonStr(publishDTO.getImages()));
        }
        
        answer.setLikeCount(0);
        answer.setIsBest(0);
        answer.setStatus(1);
        
        answerMapper.insert(answer);
        questionMapper.incrementAnswerCount(publishDTO.getQuestionId());
        try {
            QaQuestion q = questionMapper.selectById(publishDTO.getQuestionId());
            if (q != null && q.getUserId() != null && !q.getUserId().equals(userId)) {
                com.medifood.qa.notify.Notification n = new com.medifood.qa.notify.Notification();
                n.setRecipientId(q.getUserId());
                n.setActorId(userId);
                n.setType(2);
                n.setEntityType(4);
                n.setEntityId(publishDTO.getQuestionId());
                n.setExtraId(0L);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
        } catch (Exception ignored) {}
        
        log.info("发布回答成功，回答ID: {}, 问题ID: {}, 用户ID: {}", answer.getId(), publishDTO.getQuestionId(), userId);

        // 第二层防线：AI 异步审核（先发后审）
        auditEventPublisher.publish(new ContentAuditEvent(
                answer.getId(), userId, ContentAuditEvent.TYPE_ANSWER,
                null, answer.getContent(), publishDTO.getImages()));

        return answer.getId();
    }
    
    @Override
    public Page<AnswerDTO> getAnswerList(Long questionId, Integer page, Integer size, Long currentUserId) {
        Page<QaAnswer> answerPage = new Page<>(page, size);
        LambdaQueryWrapper<QaAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaAnswer::getQuestionId, questionId)
                .eq(QaAnswer::getStatus, 1)
                .orderByDesc(QaAnswer::getIsBest)
                .orderByDesc(QaAnswer::getLikeCount)
                .orderByDesc(QaAnswer::getCreateTime);
        
        Page<QaAnswer> result = answerMapper.selectPage(answerPage, wrapper);
        Page<AnswerDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<AnswerDTO> dtoList = result.getRecords().stream()
                .map(answer -> convertAnswerToDTO(answer, currentUserId))
                .collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeAnswer(Long answerId, Long userId) {
        LambdaQueryWrapper<QaAnswerLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaAnswerLike::getAnswerId, answerId)
                .eq(QaAnswerLike::getUserId, userId);
        
        if (answerLikeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已经点赞过了");
        }
        
        QaAnswerLike like = new QaAnswerLike();
        like.setAnswerId(answerId);
        like.setUserId(userId);
        answerLikeMapper.insert(like);
        
        answerMapper.incrementLikeCount(answerId);
        try {
            QaAnswer a = answerMapper.selectById(answerId);
            if (a != null && a.getUserId() != null && !a.getUserId().equals(userId)) {
                com.medifood.qa.notify.Notification n = new com.medifood.qa.notify.Notification();
                n.setRecipientId(a.getUserId());
                n.setActorId(userId);
                n.setType(1);
                n.setEntityType(4);
                n.setEntityId(a.getQuestionId());
                n.setExtraId(answerId);
                n.setIsRead(0);
                n.setCreateTime(new java.util.Date());
                notificationService.send(n);
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeAnswer(Long answerId, Long userId) {
        LambdaQueryWrapper<QaAnswerLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaAnswerLike::getAnswerId, answerId)
                .eq(QaAnswerLike::getUserId, userId);
        
        QaAnswerLike like = answerLikeMapper.selectOne(wrapper);
        if (like == null) {
            throw new BusinessException("还未点赞");
        }
        
        answerLikeMapper.deleteById(like.getId());
        answerMapper.decrementLikeCount(answerId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setBestAnswer(Long questionId, Long answerId, Long userId) {
        QaQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ResultCode.QUESTION_NOT_EXIST);
        }
        
        if (!question.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        QaAnswer answer = answerMapper.selectById(answerId);
        if (answer == null || !answer.getQuestionId().equals(questionId)) {
            throw new BusinessException(ResultCode.ANSWER_NOT_EXIST);
        }
        
        answerMapper.unsetOtherBest(questionId, answerId);
        answerMapper.setBest(answerId);
        questionMapper.setBestAnswer(questionId, answerId);
        
        log.info("设置最佳答案成功，问题ID: {}, 回答ID: {}", questionId, answerId);
    }

    @Override
    public Page<QuestionDTO> getUserQuestions(Long userId, Integer page, Integer size, Long currentUserId) {
        if (userId == null) return new Page<>(page, size, 0);
        Page<QaQuestion> p = new Page<>(page, size);
        LambdaQueryWrapper<QaQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaQuestion::getUserId, userId)
                .eq(QaQuestion::getStatus, 1)
                .orderByDesc(QaQuestion::getCreateTime);
        Page<QaQuestion> result = questionMapper.selectPage(p, wrapper);
        return convertQuestionToDTOPage(result, currentUserId);
    }

    @Autowired
    private com.medifood.qa.notify.NotificationServiceImpl notificationService;

    @Override
    public Page<QuestionDTO> getUserLikedQuestions(Long userId, Integer page, Integer size, Long currentUserId) {
        log.info("查询用户点赞问题列表, userId: {}, page: {}, size: {}, currentUserId: {}", userId, page, size, currentUserId);
        if (userId == null) return new Page<>(page, size, 0);
        
        List<QaQuestionLike> likes = questionLikeMapper.selectList(
                new LambdaQueryWrapper<QaQuestionLike>()
                        .eq(QaQuestionLike::getUserId, userId)
        );
        log.info("用户 {} 在数据库中的点赞问题记录数: {}", userId, likes.size());
        
        List<Long> ids = likes.stream()
                .map(QaQuestionLike::getQuestionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        log.info("用户 {} 点赞的有效问题 ID 列表: {}", userId, ids);
        
        if (ids.isEmpty()) {
            log.info("用户 {} 没有点赞任何有效问题，返回空页面", userId);
            return new Page<>(page, size, 0);
        }
        
        Page<QaQuestion> p = new Page<>(page, size);
        LambdaQueryWrapper<QaQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(QaQuestion::getId, ids)
                .eq(QaQuestion::getStatus, 1)
                .orderByDesc(QaQuestion::getCreateTime);
        
        Page<QaQuestion> result = questionMapper.selectPage(p, wrapper);
        log.info("最终查询到的有效问题记录数: {}", result.getRecords().size());
        
        // 打印问题标题方便调试
        result.getRecords().forEach(q -> log.info("点赞列表中的问题: ID={}, Title={}", q.getId(), q.getTitle()));
        
        return convertQuestionToDTOPage(result, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long questionId, Long userId) {
        QaQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ResultCode.QUESTION_NOT_EXIST);
        }
        if (!question.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        question.setStatus(2); // 已删除
        questionMapper.updateById(question);

        // 同步到 Elasticsearch 删除
        try {
            elasticsearchUtil.deleteDocument("qa_question_index", questionId.toString());
        } catch (Exception e) {
            log.error("从ES删除问题失败，问题ID: {}", questionId, e);
        }
        log.info("删除问题成功，问题ID: {}, 用户ID: {}", questionId, userId);
    }
    
    private Page<QuestionDTO> convertQuestionToDTOPage(Page<QaQuestion> questionPage, Long currentUserId) {
        Page<QuestionDTO> dtoPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        List<QuestionDTO> dtoList = questionPage.getRecords().stream()
                .map(q -> convertQuestionToDTO(q, currentUserId))
                .collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }
    
    private QuestionDTO convertQuestionToDTO(QaQuestion question, Long currentUserId) {
        QuestionDTO dto = new QuestionDTO();
        BeanUtils.copyProperties(question, dto);
        
        if (StrUtil.isNotBlank(question.getImages())) {
            dto.setImages(JSONUtil.toList(question.getImages(), String.class));
        }
        
        if (question.getCategoryId() != null) {
            QaCategory category = categoryMapper.selectById(question.getCategoryId());
            if (category != null) {
                dto.setCategoryName(category.getCategoryName());
            }
        }
        
        if (currentUserId != null) {
            dto.setIsLiked(questionLikeMapper.selectCount(
                    new LambdaQueryWrapper<QaQuestionLike>()
                            .eq(QaQuestionLike::getQuestionId, question.getId())
                            .eq(QaQuestionLike::getUserId, currentUserId)
            ) > 0);
        }
        
        return dto;
    }
    
    private AnswerDTO convertAnswerToDTO(QaAnswer answer, Long currentUserId) {
        AnswerDTO dto = new AnswerDTO();
        BeanUtils.copyProperties(answer, dto);
        dto.setIsBest(answer.getIsBest() == 1);
        
        if (StrUtil.isNotBlank(answer.getImages())) {
            dto.setImages(JSONUtil.toList(answer.getImages(), String.class));
        }
        
        if (currentUserId != null) {
            dto.setIsLiked(answerLikeMapper.selectCount(
                    new LambdaQueryWrapper<QaAnswerLike>()
                            .eq(QaAnswerLike::getAnswerId, answer.getId())
                            .eq(QaAnswerLike::getUserId, currentUserId)
            ) > 0);
        }
        
        return dto;
    }
}

