package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AdminOverviewVO;
import com.liuzhangjie.wardrobe.entity.Outfit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OutfitMapper extends BaseMapper<Outfit> {
    @Select("""
        SELECT CASE source WHEN 1 THEN '手动搭配' WHEN 2 THEN 'AI 生成' ELSE '其他' END AS name,
               COUNT(*) AS `value`
        FROM outfit
        WHERE deleted = 0
        GROUP BY source
        """)
    List<AdminOverviewVO.NameValueVO> countBySource();

    /**
     * 按场景统计搭配套数，管理端导出「场景分布」用。
     * <p>
     * 用 {@code IFNULL(NULLIF(scene,''), '未标注')} 而不是 IFNULL：
     * 场景列既可能是 NULL（老数据没填），也可能是空字符串（前端传了空），
     * 只判 NULL 会把空字符串分成单独一组，导出里就出现一个名字为空的怪异行。
     */
    @Select("""
        SELECT IFNULL(NULLIF(scene, ''), '未标注') AS name, COUNT(*) AS `value`
        FROM outfit
        WHERE deleted = 0
        GROUP BY IFNULL(NULLIF(scene, ''), '未标注')
        ORDER BY `value` DESC
        """)
    List<AdminOverviewVO.NameValueVO> countByScene();

}
