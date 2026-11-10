package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

import java.util.List;

@Data
public class OutfitDTO {
    private String name;
    private String scene;
    private Integer season;
    private String description;
    private String coverUrl;
    private List<OutfitItem> items;//复用 OutfitItem → 少建一个类，但那个类里有 id、outfitId、createTime 等一堆用不上的字段
}
