package com.medifood.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.recipe.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 食谱 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {
    
    @Update("UPDATE recipe SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);
    
    @Update("UPDATE recipe SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE recipe SET like_count = like_count - 1 WHERE id = #{id}")
    void decrementLikeCount(@Param("id") Long id);
    
    @Update("UPDATE recipe SET comment_count = comment_count + 1 WHERE id = #{id}")
    void incrementCommentCount(@Param("id") Long id);

    @Update("UPDATE recipe SET comment_count = comment_count - 1 WHERE id = #{id}")
    void decrementCommentCount(@Param("id") Long id);

    @Update("UPDATE recipe SET collect_count = collect_count + 1 WHERE id = #{id}")
    void incrementCollectCount(@Param("id") Long id);

    @Update("UPDATE recipe SET collect_count = collect_count - 1 WHERE id = #{id}")
    void decrementCollectCount(@Param("id") Long id);

    /**
     * 根据节气名称精确查询食谱（使用 FIND_IN_SET 匹配 suitable_seasons 字段）
     * <p>
     * suitable_seasons 字段存储格式为逗号分隔的节气/季节名称，如 "立春,春分,春季"
     * </p>
     *
     * @param seasonName 节气名称（如 "立春"）
     * @param limit      返回条数限制
     * @return 匹配的食谱列表
     */
    @Select("SELECT * FROM recipe WHERE status = 1 AND FIND_IN_SET(#{seasonName}, suitable_seasons) > 0 LIMIT #{limit}")
    List<Recipe> findBySeason(@Param("seasonName") String seasonName, @Param("limit") int limit);
}

