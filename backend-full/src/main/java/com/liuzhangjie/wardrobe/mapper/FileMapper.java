package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * file 表的数据访问层。
 * <p>
 * 必须继承 BaseMapper，insert / selectById / deleteById 这些方法才存在；
 * 不继承就是一个空接口，调用时编译期就报「找不到方法」。
 */
@Mapper
public interface FileMapper extends BaseMapper<FileRecord> {
}
