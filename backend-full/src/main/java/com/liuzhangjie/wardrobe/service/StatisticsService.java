package com.liuzhangjie.wardrobe.service;

import com.liuzhangjie.wardrobe.entity.AdminOverviewVO;
import com.liuzhangjie.wardrobe.entity.OverviewVO;

import java.util.List;
import java.util.Map;

public interface StatisticsService {

    /**
     * 当前用户的数据概览：衣物数、搭配数。
     * 不带 userId 参数，用户身份从 token 里来。
     */
    OverviewVO getOverview();

    /**
     * 管理端--数据报表
     * @return
     */
    AdminOverviewVO getAdminOverview();

    /**
     * 管理端--数据导出的行数据。
     * <p>
     * 为什么返回 {@code List<Map>} 而不是定义三个专门的 VO：
     * 导出是"字段白名单"最需要显式表达的地方——用 Map 就得一个个 put，
     * 想多带一个字段必须动代码、过 review；用 VO 的话加个 getter 就悄悄多导出了一列。
     * 这里刻意选更难写、但更难出错的写法。
     *
     * @param type users / category-stats / scene-stats
     */
    List<Map<String, Object>> exportRows(String type);
}
