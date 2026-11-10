package com.liuzhangjie.wardrobe.controller;

import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.entity.OverviewVO;
import com.liuzhangjie.wardrobe.service.StatisticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics")
@Tag(name = "统计相关接口")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 个人中心「我的数据」：衣物数 + 搭配数。
     * 这个路径没有加进 SecurityConfig 的放行清单，所以必须带 token 才能访问。
     */
    @GetMapping("/overview")
    public Result<OverviewVO> getOverview() {
        return Result.ok(statisticsService.getOverview());
    }

}
