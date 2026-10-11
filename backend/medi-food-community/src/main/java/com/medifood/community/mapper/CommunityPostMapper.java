package com.medifood.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.community.entity.CommunityPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {
    
    @Select("SELECT p.* FROM community_post p INNER JOIN post_like l ON p.id = l.post_id WHERE l.user_id = #{userId} ORDER BY l.create_time DESC")
    Page<CommunityPost> selectLikedPosts(Page<CommunityPost> page, @Param("userId") Long userId);

    @Select("SELECT p.* FROM community_post p INNER JOIN post_collect c ON p.id = c.post_id WHERE c.user_id = #{userId} ORDER BY c.create_time DESC")
    Page<CommunityPost> selectCollectedPosts(Page<CommunityPost> page, @Param("userId") Long userId);


    @Update("UPDATE community_post SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE community_post SET like_count = like_count - 1 WHERE id = #{id}")
    void decrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE community_post SET comment_count = comment_count + 1 WHERE id = #{id}")
    void incrementCommentCount(@Param("id") Long id);
}

