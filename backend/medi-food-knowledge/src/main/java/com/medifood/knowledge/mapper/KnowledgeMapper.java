package com.medifood.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.knowledge.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<Knowledge> {
    
    /**
     * 增加浏览次数
     */
    @Update("UPDATE knowledge SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);
    
    /**
     * 增加点赞数
     */
    @Update("UPDATE knowledge SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementLikeCount(@Param("id") Long id);
    
    /**
     * 减少点赞数
     */
    @Update("UPDATE knowledge SET like_count = like_count - 1 WHERE id = #{id}")
    void decrementLikeCount(@Param("id") Long id);
    
    /**
     * 增加评论数
     */
    @Update("UPDATE knowledge SET comment_count = comment_count + 1 WHERE id = #{id}")
    void incrementCommentCount(@Param("id") Long id);
    
    /**
     * 增加收藏数
     */
    @Update("UPDATE knowledge SET collect_count = collect_count + 1 WHERE id = #{id}")
    void incrementCollectCount(@Param("id") Long id);
    
    /**
     * 减少收藏数
     */
    @Update("UPDATE knowledge SET collect_count = collect_count - 1 WHERE id = #{id}")
    void decrementCollectCount(@Param("id") Long id);
   /*
   * 查询所有知识
   * */

}

