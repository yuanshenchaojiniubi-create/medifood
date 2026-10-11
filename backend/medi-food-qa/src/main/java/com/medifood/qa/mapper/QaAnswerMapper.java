package com.medifood.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.qa.entity.QaAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QaAnswerMapper extends BaseMapper<QaAnswer> {
    
    @Update("UPDATE qa_answer SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE qa_answer SET like_count = like_count - 1 WHERE id = #{id}")
    void decrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE qa_answer SET is_best = 1 WHERE id = #{id}")
    void setBest(@Param("id") Long id);
    
    @Update("UPDATE qa_answer SET is_best = 0 WHERE question_id = #{questionId} AND id != #{id}")
    void unsetOtherBest(@Param("questionId") Long questionId, @Param("id") Long id);
}

