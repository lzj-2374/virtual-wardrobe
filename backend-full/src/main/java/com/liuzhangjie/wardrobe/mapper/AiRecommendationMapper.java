package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AiRecommendation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 推荐结果表。
 * <p>
 * 多了一个自定义 SQL：按「衣物 id」反查推荐 id。
 * 用途是级联失效——用户删掉一件衣服时，之前生成过的推荐里有的就缺件了，
 * 需要把这些推荐一并标脏，否则点「放入我的搭配」会搬进一套缺衣服的搭配。
 */
@Mapper
public interface AiRecommendationMapper extends BaseMapper<AiRecommendation> {

    /**
     * 找出「引用了某件衣物、且还有效」的推荐 id 列表。
     * <p>
     * 必须 JOIN 到 ai_recommendation_item 才能按 clothing_id 过滤，
     * 同时要带 r.user_id：不加的话，A 用户删衣服会把 B 用户引用同名 id 的推荐也标脏。
     */
    @Select("""
            SELECT DISTINCT r.id
            FROM ai_recommendation r
            JOIN ai_recommendation_item i ON i.recommendation_id = r.id
            WHERE i.clothing_id = #{clothingId}
              AND r.user_id = #{userId}
              AND r.deleted = 0
            """)
    java.util.List<Long> findIdsByClothingId(@Param("clothingId") Long clothingId,
                                             @Param("userId") Long userId);
}
