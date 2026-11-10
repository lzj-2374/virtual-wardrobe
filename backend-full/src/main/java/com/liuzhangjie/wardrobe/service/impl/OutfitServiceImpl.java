package com.liuzhangjie.wardrobe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.common.BusinessException;
import com.liuzhangjie.wardrobe.common.ResultCode;
import com.liuzhangjie.wardrobe.entity.AiRecommendation;
import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.Outfit;
import com.liuzhangjie.wardrobe.entity.OutfitDTO;
import com.liuzhangjie.wardrobe.entity.OutfitItem;
import com.liuzhangjie.wardrobe.mapper.AiRecommendationMapper;
import com.liuzhangjie.wardrobe.mapper.ClothingMapper;
import com.liuzhangjie.wardrobe.mapper.OutfitItemMapper;
import com.liuzhangjie.wardrobe.mapper.OutfitMapper;
import com.liuzhangjie.wardrobe.service.OutfitService;
import com.liuzhangjie.wardrobe.utils.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OutfitServiceImpl implements OutfitService {

    private final OutfitMapper outfitMapper;
    private final OutfitItemMapper outfitItemMapper;
    private final ClothingMapper clothingMapper;
    private final AiRecommendationMapper aiRecommendationMapper;

    public OutfitServiceImpl(OutfitMapper outfitMapper,
                             OutfitItemMapper outfitItemMapper,
                             ClothingMapper clothingMapper,
                             AiRecommendationMapper aiRecommendationMapper) {
        this.outfitMapper = outfitMapper;
        this.outfitItemMapper = outfitItemMapper;
        this.clothingMapper = clothingMapper;
        this.aiRecommendationMapper = aiRecommendationMapper;
    }

    /**
     * 分页
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return
     */
    @Override
    public Page<Outfit> page(Integer page, Integer size,String keyword,String  scene) {
        // userId 一律从登录态取，不从请求参数接，否则能翻到别人的搭配
        Long userId = UserContext.getUserId();

        // ===== 第 1 次 SQL：分页查主表 =====
        Page<Outfit> pageParam = new Page<>(page, size);
        Page<Outfit> result = outfitMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Outfit>()
                        .eq(Outfit::getUserId, userId)
                        .like(keyword != null && !keyword.isBlank(), Outfit::getName, keyword)
                        .eq(scene != null && !scene.isBlank(), Outfit::getScene, scene)
                        .orderByDesc(Outfit::getCreateTime));

        List<Outfit> records = result.getRecords();
        // 这一页没有数据就直接返回，别再去查一次 item 表
        if (records.isEmpty()) {
            return result;
        }

        // ===== 第 2 次 SQL：一次把这页所有搭配的单品捞出来 =====
        // 关键点：用 in + 收集好的 id 列表，而不是 for 循环里一条一条查（那样就是 N+1 次 SQL）
        List<Long> outfitIds = records.stream().map(Outfit::getId).toList();
        List<OutfitItem> allItems = outfitItemMapper.selectList(
                new LambdaQueryWrapper<OutfitItem>()
                        .in(OutfitItem::getOutfitId, outfitIds)
                        .orderByAsc(OutfitItem::getRole));

        // ===== 在内存里按 outfitId 分组，再塞回各自的搭配 =====
        Map<Long, List<OutfitItem>> grouped = allItems.stream()
                .collect(Collectors.groupingBy(OutfitItem::getOutfitId));
        for (Outfit outfit : records) {
            // 没有单品的搭配给个空列表，别留 null —— 前端 items?.length 遇到 null 显示 0，
            // 但以后写"遍历 items"的代码时 null 会直接 NPE
            outfit.setItems(grouped.getOrDefault(outfit.getId(), new ArrayList<>()));
        }

        return result;
    }

    /**
     * 新增搭配
     * @param dto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(OutfitDTO dto) {
        //1获取当前用户id
        Long userId = UserContext.getUserId();

        //2校验：名称、单品列表、角色合法性、衣物归属
        //  这套规则 update 也要用，所以抽成 validate 共用一份，避免两处各写一遍
        validate(dto, userId);

        //3插主表
        //  新建一个 Outfit 逐个 set，而不是直接插 dto：这样等于白名单，
        //  只有这里 set 过的字段能进库，前端多塞的字段（比如 source=2）一律丢弃
        Outfit outfit = new Outfit();
        outfit.setUserId(userId);
        outfit.setName(dto.getName().trim());
        outfit.setScene(dto.getScene());
        //season 为空兜底成 15（四季），否则以后按季节筛选会漏掉这条
        outfit.setSeason(dto.getSeason() == null ? 15 : dto.getSeason());
        outfit.setDescription(dto.getDescription());
        outfit.setCoverUrl(dto.getCoverUrl());
        //这个接口只产生手动搭配，硬编码 1；source=2 只允许 AI 模块那条路径写
        outfit.setSource(1);
        outfit.setLikeFlag(0);
        //createTime / updateTime / deleted 都不 set，交给数据库默认值和自动更新
        outfitMapper.insert(outfit);

        //4挂单品
        saveItems(outfit.getId(), dto.getItems());

        return outfit.getId();
    }

    /**
     * 详情
     * @param id
     */
    @Override
    public Outfit detail(Long id) {
        //取id
        Long userId = UserContext.getUserId();

        //查主表
        Outfit outfit = outfitMapper.selectOne(new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getId, id)
                .eq(Outfit::getUserId, userId));
        if (outfit==null){
            throw new BusinessException(ResultCode.OUTFIT_NOT_FOUND);
        }

        //查从表
        List<OutfitItem> itemList = outfitItemMapper.selectList(new LambdaQueryWrapper<OutfitItem>()
                .eq(OutfitItem::getOutfitId, id)
                .orderByAsc(OutfitItem::getRole)
        );

        //每件单品信息(toset天然去重)
        Set<Long> clothingIds = itemList.stream()
                .map(OutfitItem::getClothingId)
                .filter(Objects::nonNull) // 防止null丢进in查询
                .collect(Collectors.toSet());

        //一次查回来（注意传的是集合 clothingIds，不是搭配 id）
        List<Clothing> clothingList = clothingMapper.selectList(new LambdaQueryWrapper<Clothing>()
                .in(Clothing::getId, clothingIds));

        //List 转 Map，之后按 id 取就快了
        Map<Long, Clothing> clothingMap = clothingList.stream()
                .collect(Collectors.toMap(Clothing::getId, c -> c));

        //将衣物循环挂载到单品上
        for (OutfitItem item : itemList) {
            item.setClothing(clothingMap.get(item.getClothingId()));
        }

        //查完放进去
        outfit.setItems(itemList);

        //返回
        return outfit;
    }

    /**
     * 修改搭配
     * @param id
     * @param dto
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OutfitDTO dto) {
        Long userId = UserContext.getUserId();

        //1查归属：必须同时带 id 和 userId，只按 id 查就能改到别人的搭配
        Outfit outfit = outfitMapper.selectOne(new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getId, id)
                .eq(Outfit::getUserId, userId));
        if (outfit == null) {
            throw new BusinessException(ResultCode.OUTFIT_NOT_FOUND);
        }

        //2校验：跟新增同一套规则。注意这步不能省——update 的请求体同样能把别人的衣服塞进来
        validate(dto, userId);

        //3把 dto 的新值搬到查出来的对象上
        //  这一步最容易漏：updateById 更新的是"你手上这个对象"，
        //  不搬的话对象里还是数据库的旧值，写回去等于什么都没改。
        //  只搬允许改的 5 个字段，userId / source / likeFlag / createTime 一个都不能碰：
        //  source 被改成 null 的话，一条 AI 来源的搭配会悄悄变成"手动搭配"
        outfit.setName(dto.getName().trim());
        outfit.setScene(dto.getScene());
        outfit.setSeason(dto.getSeason() == null ? 15 : dto.getSeason());
        outfit.setDescription(dto.getDescription());
        outfit.setCoverUrl(dto.getCoverUrl());

        //4更新主表
        outfitMapper.updateById(outfit);

        //5删掉旧单品
        //  必须用 delete(条件) 而不是 deleteById(主键)：
        //  outfit_item 的主键是它自己的 id，不是 outfit_id，用 deleteById 会删错行。
        //  也必须真的删：这张表没有 deleted 字段，旧记录留着就会跟新插入的撞 uk_outfit_role 唯一索引
        outfitItemMapper.delete(new LambdaQueryWrapper<OutfitItem>()
                .eq(OutfitItem::getOutfitId, id));

        //6插入新单品
        saveItems(id, dto.getItems());
    }

    /**
     * 删除
     * @param id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id) {
        //查归属
        Long userId = UserContext.getUserId();
        Outfit outfit = outfitMapper.selectOne(new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getId, id)
                .eq(Outfit::getUserId, userId));
        if(outfit==null){
            throw new BusinessException(ResultCode.OUTFIT_NOT_FOUND);
        }

        // 删从表：一条 SQL 删掉这套搭配的所有单品，不用先查
        outfitItemMapper.delete(new LambdaQueryWrapper<OutfitItem>()
                .eq(OutfitItem::getOutfitId, id));

        //删主表
        outfitMapper.deleteById(id);

        // 如果这条搭配是从 AI 推荐"转正"过来的，要把推荐那边也标脏，
        // 否则 AI 助手里的卡片还显示"已放入我的搭配"，但点"去查看"却找不到
        invalidateSourceRecommendation(id);
    }

    /**
     * 搭配被删后，解除推荐与它的绑定。
     * <p>
     * 只重置 saved / outfitId，不把整条推荐标记为失效：
     * 用户删掉搭配多半是想"重新放一次"，推荐本身的内容（名称、理由、单品）还是有效的，
     * 留着让他能再点一次，比逼他重新问一遍 AI 更合理。
     */
    private void invalidateSourceRecommendation(Long outfitId) {
        // 用 UpdateWrapper 而不是"查出来改字段再 updateById"：
        // MyBatis-Plus 的 updateById 默认忽略 null 字段，outfit_id 想置回 null 必须显式 set
        aiRecommendationMapper.update(null, new LambdaUpdateWrapper<AiRecommendation>()
                .set(AiRecommendation::getSaved, 0)
                .set(AiRecommendation::getOutfitId, null)
                .eq(AiRecommendation::getOutfitId, outfitId));
    }

    /**
     * 校验入参，add 和 update 共用。
     * 顺序是有意的：先做不查库的检查（便宜），最后才做归属查询（贵），
     * 避免参数明显不合法时还白跑一次数据库。
     */
    private void validate(OutfitDTO dto, Long userId) {
        //2.1判断名称是否为空
        //   用 isBlank 而不是 isEmpty：纯空格也算空，否则能存进去一个名字全是空格的搭配
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        //2.2判断items是否为空（一件单品都没有的搭配，存进去没有意义）
        //   注意先判 null 再判 isEmpty：OutfitDTO 里的 items 没有初始化默认值，
        //   前端不传这个字段时反序列化出来就是 null，直接调 isEmpty() 会 NPE
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(ResultCode.OUTFIT_ITEM_EMPTY);
        }

        //2.3逐条校验单品，同时把 clothingId 收进 Set
        //    用 Set 而不是 List：同一件衣服可以同时占两个角色（卫衣既算上装又算外套），
        //    用 List 会收集出两个相同 id，下面跟数据库 count 对比时数量对不上，误判成"无效衣物"
        Set<Integer> roles = new HashSet<>();
        Set<Long> clothingIds = new HashSet<>();
        for (OutfitItem item : dto.getItems()) {
            if (item.getRole() == null || item.getRole() < 1 || item.getRole() > 5
                    || item.getClothingId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            //roles.add 返回 false，说明这个角色在前面已经出现过了
            if (!roles.add(item.getRole())) {
                throw new BusinessException(ResultCode.OUTFIT_ROLE_DUPLICATE);
            }
            clothingIds.add(item.getClothingId());
        }

        //2.4确认这些衣服都是当前用户的
        //    只查总数，不做逐条查询：5 件衣服查 1 次，比查 5 次省 4 次数据库往返。
        //    代价是报错只能笼统说"包含无效衣物"，指不出具体哪一件——自己给自己建搭配，够用了
        Long owned = clothingMapper.selectCount(new LambdaQueryWrapper<Clothing>()
                .in(Clothing::getId, clothingIds)
                .eq(Clothing::getUserId, userId));
        if (!owned.equals((long) clothingIds.size())) {
            throw new BusinessException(ResultCode.OUTFIT_ITEM_INVALID);
        }
    }

    /**
     * 把一批单品挂到指定搭配上，add 和 update 共用。
     * 调用前必须保证 outfit_item 里没有该搭配的旧记录（update 里已经先删了）。
     */
    private void saveItems(Long outfitId, List<OutfitItem> items) {
        //必须 new 一个新的 OutfitItem，不能直接插 dto 里那个对象：
        //那个对象是前端 JSON 反序列化来的，可能带着 id 或别人的 outfitId
        for (OutfitItem src : items) {
            OutfitItem item = new OutfitItem();
            item.setOutfitId(outfitId);
            item.setClothingId(src.getClothingId());
            item.setRole(src.getRole());
            item.setSort(0);
            outfitItemMapper.insert(item);
        }
    }

}
