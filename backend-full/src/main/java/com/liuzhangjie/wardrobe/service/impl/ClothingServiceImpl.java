package com.liuzhangjie.wardrobe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.common.BusinessException;
import com.liuzhangjie.wardrobe.common.EnableStatus;
import com.liuzhangjie.wardrobe.common.ResultCode;
import com.liuzhangjie.wardrobe.ai.ClothingRecognizer;
import com.liuzhangjie.wardrobe.entity.AiRecommendation;
import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.ClothingDTO;
import com.liuzhangjie.wardrobe.entity.ClothingTag;
import com.liuzhangjie.wardrobe.entity.OutfitItem;
import com.liuzhangjie.wardrobe.entity.RecognizeDTO;
import com.liuzhangjie.wardrobe.mapper.AiRecommendationMapper;
import com.liuzhangjie.wardrobe.mapper.ClothingMapper;
import com.liuzhangjie.wardrobe.mapper.ClothingTagMapper;
import com.liuzhangjie.wardrobe.mapper.OutfitItemMapper;
import com.liuzhangjie.wardrobe.service.ClothingService;
import com.liuzhangjie.wardrobe.utils.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ClothingServiceImpl implements ClothingService {

    private final ClothingMapper clothingMapper;
    private final ClothingTagMapper clothingTagMapper;
    private final OutfitItemMapper outfitItemMapper;
    private final AiRecommendationMapper aiRecommendationMapper;
    private final ClothingRecognizer clothingRecognizer;

    public ClothingServiceImpl(ClothingMapper clothingMapper,
                               ClothingTagMapper clothingTagMapper,
                               OutfitItemMapper outfitItemMapper,
                               AiRecommendationMapper aiRecommendationMapper,
                               ClothingRecognizer clothingRecognizer) {
        this.clothingMapper = clothingMapper;
        this.clothingTagMapper = clothingTagMapper;
        this.outfitItemMapper = outfitItemMapper;
        this.aiRecommendationMapper = aiRecommendationMapper;
        this.clothingRecognizer = clothingRecognizer;
    }

    /**
     * 分页查询
     * @param page     第几页，从 1 开始
     * @param size     每页几条
     * @param category 品类，可空
     * @param keyword  名称关键字，可空
     * @param season   季节位标识（1/2/4/8/15），可空
     * @return
     */
    @Override
    public Page<Clothing> pageQuery(Integer page, Integer size, Integer category, String keyword, Integer season) {
        //获取当前用户
        Long userId = UserContext.getUserId();

        //创建分页对象
        Page<Clothing> clothingPage = new Page<>(page, size);

        //创建条件容器对象
        LambdaQueryWrapper<Clothing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Clothing::getUserId, userId);
        wrapper.eq(category != null, Clothing::getCategory, category);
        wrapper.like(StringUtils.hasText(keyword), Clothing::getName, keyword);
        wrapper.apply(season != null, "season & {0} > 0", season);
        //加一行排序规则，否则 MySQL 不保证返回顺序，翻页时同一条数据可能出现在两页里
        wrapper.orderByDesc(Clothing::getCreateTime);

        //返回
        return clothingMapper.selectPage(clothingPage,wrapper);
    }

    /**
     * 详情页？
     * @param id
     * @return
     */
    @Override
    public Clothing detail(Long id) {
        Long userId = UserContext.getUserId();
        Clothing clothing = clothingMapper.selectOne(
                new LambdaQueryWrapper<Clothing>()
                        .eq(Clothing::getId, id)
                        .eq(Clothing::getUserId, userId)
        );

        if(clothing==null){
            throw new BusinessException(ResultCode.CLOTHING_NOT_FOUND);
        }

        //去 clothing_tag 表把属于这件衣服的所有标签查出来
        List<ClothingTag> tags = clothingTagMapper.selectList(
                new LambdaQueryWrapper<ClothingTag>()
                        .eq(ClothingTag::getClothingId, id));
        clothing.setTags(tags);


        return clothing;
    }

    /**
     * 添加衣物
     * @param dto
     * @return
     */
    @Override
    @Transactional
    public void add(ClothingDTO dto) {
        //先取出当前用户id
        Long userId=UserContext.getUserId();

        //组装clothing
        Clothing clothing = new Clothing();
        BeanUtils.copyProperties(dto,clothing);
        clothing.setUserId(userId);
        clothing.setStatus(EnableStatus.ENABLED.getCode());//状态码，1正常，0禁用

        //插入主表。执行完这一行，MyBatis-Plus 会把数据库生成的自增主键回填到 clothing.id，
        //所以下一步才能拿到正确的衣物 id 去关联标签
        clothingMapper.insert(clothing);

        //遍历标签，逐个挂到刚才生成的衣物 id 上
        for (ClothingTag tag : dto.getTags()) {
            tag.setClothingId(clothing.getId());   // ← 用的就是上一步回填的 id
            clothingTagMapper.insert(tag);
        }
        
    }

    /**
     * 修改衣物
     * @param id
     * @param dto
     */
    @Override
    @Transactional
    public void update(Long id, ClothingDTO dto) {
        //先确认衣服是否是本人的
        Long userId = UserContext.getUserId();
        Clothing clothing = clothingMapper.selectOne(new LambdaQueryWrapper<Clothing>()
                .eq(Clothing::getId, id)
                .eq(Clothing::getUserId, userId));


        if (clothing == null) {
            // 不属于当前用户，抛异常，不允许修改
            throw new BusinessException(ResultCode.CLOTHING_NO_PERMISSION);
        }
        //对象拷贝
        BeanUtils.copyProperties(dto,clothing);
        //修改
        clothingMapper.updateById(clothing);
        //删除旧标签
        clothingTagMapper.delete(new LambdaQueryWrapper<ClothingTag>()
                .eq(ClothingTag::getClothingId, id));

        //新建标签
        for (ClothingTag tag : dto.getTags()) {
            tag.setId(null);          // 抹掉前端带回来的旧主键，让数据库重新分配
            tag.setClothingId(id);
            clothingTagMapper.insert(tag);
        }

    }

    /**
     * 删除单件
     * @param id
     */
    @Override
    @Transactional
    public void delete(Long id) {
        //查归属
        Long userId=UserContext.getUserId();
        Clothing clothing = clothingMapper.selectOne(new LambdaQueryWrapper<Clothing>()
                .eq(Clothing::getId, id)
                .eq(Clothing::getUserId, userId)
        );
        if(clothing==null){
            throw new BusinessException(ResultCode.CLOTHING_NO_PERMISSION);
        }
        //先检查是否被搭配引用
        Long usedCount = outfitItemMapper.selectCount(
                new LambdaQueryWrapper<OutfitItem>().eq(OutfitItem::getClothingId, id));
        if (usedCount > 0) {
            throw new BusinessException(ResultCode.CLOTHING_IN_USE);
        }
        //删除
        clothingMapper.deleteById(id);
        //删标签
        clothingTagMapper.delete(new LambdaQueryWrapper<ClothingTag>()
                .eq(ClothingTag::getClothingId, id));
        // AI 推荐里引用了这件衣服的，一并标脏
        invalidateAiRecommendations(id);

    }

    /**
     * 批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteByIds(List<Long> ids) {

        if (ids == null || ids.isEmpty()) {
            return;   // 或者抛 PARAM_ERROR
        }

        //查归属
        Long userId = UserContext.getUserId();
        //如果查到的数量 和 前端传的ids数量不一致 → 说明里面混入别人的衣服 / 有id不存在
        Long clothingCount = clothingMapper.selectCount(new LambdaQueryWrapper<Clothing>()
                .in(Clothing::getId, ids)
                .eq(Clothing::getUserId, userId)
        );
        if(clothingCount!=ids.size()){
            throw new BusinessException(ResultCode.CLOTHING_NO_PERMISSION);
        }
        //是否被搭配引用
        Long usedCount = outfitItemMapper.selectCount(new LambdaQueryWrapper<OutfitItem>()
                .in(OutfitItem::getClothingId, ids));
        if(usedCount>0){
            //只要有一个被引用，直接删除失败，事务回滚
            throw new BusinessException(ResultCode.CLOTHING_IN_USE);
        }
        //删除
        clothingMapper.deleteByIds(ids);

        //删除标签
        clothingTagMapper.delete(new LambdaQueryWrapper<ClothingTag>()
                .in(ClothingTag::getClothingId,ids));

        // AI 推荐里引用了这些衣服的，一并标脏
        ids.forEach(this::invalidateAiRecommendations);
    }

    /**
     * AI 识别衣物属性。
     * <p>
     * 只产出建议值，不写库。真正的落库走 {@link #add}，由用户确认后提交——
     * 如果识别完就存一条，用户改完再提交会变成两条重复记录。
     */
    @Override
    public ClothingDTO recognize(RecognizeDTO dto) {
        if (dto == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        return clothingRecognizer.recognize(dto.getFileName(), dto.getImageUrl());
    }

    /**
     * 一件衣服被删后，之前引用它的 AI 推荐就缺件了，再点「放入我的搭配」会搬进一套残缺的搭配。
     * 所以把相关推荐标成失效（deleted=1、saved=0），卡片上的按钮自然回到"未放入"。
     * <p>
     * 不往 AI 模块的 Service 里加回调，是因为删除衣物的入口只有这里，
     * 直接在这里处理比引入事件机制更直白，也不会出现"监听器没注册导致漏标"的情况。
     */
    private void invalidateAiRecommendations(Long clothingId) {
        Long userId = UserContext.getUserId();
        List<Long> recommendationIds = aiRecommendationMapper.findIdsByClothingId(clothingId, userId);
        if (recommendationIds.isEmpty()) {
            return;
        }
        AiRecommendation patch = new AiRecommendation();
        patch.setDeleted(1);
        patch.setSaved(0);
        aiRecommendationMapper.update(patch, new LambdaUpdateWrapper<AiRecommendation>()
                .in(AiRecommendation::getId, recommendationIds));
    }
}
