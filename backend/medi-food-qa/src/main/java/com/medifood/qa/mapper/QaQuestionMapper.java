package com.medifood.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.qa.entity.QaQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QaQuestionMapper extends BaseMapper<QaQuestion> {
    
    @Update("UPDATE qa_question SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);
    
    @Update("UPDATE qa_question SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE qa_question SET like_count = like_count - 1 WHERE id = #{id}")
    void decrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE qa_question SET answer_count = answer_count + 1 WHERE id = #{id}")
    void incrementAnswerCount(@Param("id") Long id);
    
    @Update("UPDATE qa_question SET best_answer_id = #{answerId} WHERE id = #{id}")
    void setBestAnswer(@Param("id") Long id, @Param("answerId") Long answerId);
}

