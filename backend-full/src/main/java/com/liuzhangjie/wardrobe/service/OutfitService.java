package com.liuzhangjie.wardrobe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.entity.Outfit;
import com.liuzhangjie.wardrobe.entity.OutfitDTO;

public interface OutfitService {

    /**
     * 分页查询当前用户的搭配列表，每条都带上它包含的单品。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数
     */
    Page<Outfit> page(Integer page, Integer size,String keyword,String scene);

    /**
     * 新增搭配
     * @param dto
     * @return
     */
    Long add(OutfitDTO dto);

    /**
     * 详情
     * @param id
     */
    Outfit detail(Long id);

    /**
     * 修改搭配
     * @param id
     * @param dto
     * @return
     */
    void update(Long id, OutfitDTO dto);

    /**
     * 删除
     * @param id
     */
    void delete(Long id);
}
