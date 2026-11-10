package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

import java.util.List;

@Data
public class AdminOverviewVO {
    private Long totalUser;
    private Long totalClothing;
    private Long totalOutfit;
    private Long todayNewUser;

    private TrendVO userTrend;                      // 折线图：两条等长数组
    private List<NameValueVO> categoryDistribution; // 饼图：品类分布
    private List<NameValueVO> sourceDistribution;   // 饼图：搭配来源

    @Data
    public static class TrendVO {
        private List<String> dates;   // ["2026-11-01", ...]
        private List<Long> values;    // [2, 0, 5, ...] 天数必须和 dates 一样长
    }

    @Data
    public static class NameValueVO {
        private String name;   // "上衣" / "手动搭配"
        private Long value;    // 128
    }

    @Data
    public static class TrendPointVO {
        private String dt;    // "2026-11-04"
        private Long cnt;     // 5
    }

}
