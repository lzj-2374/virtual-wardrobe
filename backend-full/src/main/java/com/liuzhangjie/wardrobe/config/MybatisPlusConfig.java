package com.liuzhangjie.wardrobe.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 1. 分页插件：只有注册了 PaginationInnerInterceptor，Page 对象才会真正生成 limit 语句，
 *    否则查出来永远是全表（这是新手最常踩的坑之一）。
 * 2. @MapperScan：指定 Mapper 接口所在包，省得每个接口都写 @Mapper。
 */
@Configuration
@MapperScan("com.liuzhangjie.wardrobe.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

}
