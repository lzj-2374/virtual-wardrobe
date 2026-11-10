package com.liuzhangjie.wardrobe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.ClothingDTO;
import com.liuzhangjie.wardrobe.entity.RecognizeDTO;

import java.util.List;

public interface ClothingService {

    /**
     * 分页查询当前登录用户的衣柜。
     * <p>
     * 方法参数里<b>没有 userId</b>，用户身份由实现类从 UserContext 里取，
     * 这样 Controller 就没办法把别人的 id 传进来，从结构上杜绝越权。
     *
     * @param page     第几页，从 1 开始
     * @param size     每页几条
     * @param category 品类，可空
     * @param keyword  名称关键字，可空
     * @param season   季节位标识（1/2/4/8/15），可空
     * @return 分页结果，含 records 和 total
     */
    Page<Clothing> pageQuery(Integer page, Integer size, Integer category, String keyword, Integer season);

    /**
     * tag标签
     * @param id
     * @return
     */
    Clothing detail(Long id);

    /**
     * 添加衣物
     * @param dto
     * @return
     */
    void add(ClothingDTO dto);

    /**
     * 修改衣物
     * @param id
     * @param dto
     */
    void update(Long id, ClothingDTO dto);

    /**
     * 删除单件
     * @param id
     */
    void delete(Long id);

    /**
     * 批量删除
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * AI 识别衣物属性，返回建议值供前端填表。
     * <p>
     * 注意它<b>不落库</b>：识别只是帮用户少填几个输入框，最终存什么由用户确认后调 {@link #add} 决定。
     * 如果这里直接写库，用户改几个字段再提交就会产生两条记录。
     */
    ClothingDTO recognize(RecognizeDTO dto);
}
