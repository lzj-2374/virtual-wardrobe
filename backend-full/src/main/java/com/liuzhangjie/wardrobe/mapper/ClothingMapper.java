package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AdminOverviewVO;
import com.liuzhangjie.wardrobe.entity.Clothing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClothingMapper extends BaseMapper<Clothing> {
    @Select("""
        SELECT IFNULL(c.name, '其他') AS name, COUNT(*) AS `value`
        FROM clothing cl
        LEFT JOIN clothing_category c ON c.id = cl.category AND c.deleted = 0
        WHERE cl.deleted = 0
        GROUP BY cl.category
        ORDER BY `value` DESC
        """)
    List<AdminOverviewVO.NameValueVO> countByCategory();

    @Select("""
        SELECT c.name AS name, COUNT(cl.id) AS `value`
        FROM clothing_category c
        LEFT JOIN clothing cl
               ON cl.category = c.id
              AND cl.user_id = #{userId}
              AND cl.deleted = 0
        WHERE c.level = 1 AND c.deleted = 0
        GROUP BY c.id, c.name
        ORDER BY c.sort
        """)
    List<AdminOverviewVO.NameValueVO> countCategoryByUser(@Param("userId") Long userId);

    /**
     * 工作台--风格分布：只数当前用户的衣物，且只取风格标签（tag_type = 1）。
     * <p>
     * clothing_tag 表里只有 clothing_id，没有用户归属，所以必须 JOIN clothing
     * 才能按 user_id 过滤；漏掉这个 JOIN 会把全站用户的风格一起算进来。
     * 该表没有 deleted 字段（物理删除），别画蛇添足加条件。
     */
    @Select("""
            SELECT t.tag_value AS name, COUNT(*) AS `value`
            FROM clothing_tag t
            JOIN clothing cl ON cl.id = t.clothing_id
            WHERE cl.user_id = #{userId}
              AND cl.deleted = 0
              AND t.tag_type = 1
            GROUP BY t.tag_value
            ORDER BY `value` DESC
            """)
    List<AdminOverviewVO.NameValueVO> countStyleByUser(@Param("userId") Long userId);

}
