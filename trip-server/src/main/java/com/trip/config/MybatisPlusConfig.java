package com.trip.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件 + Mapper 扫描。
 * 注意：PaginationInnerInterceptor 依赖 mybatis-plus-jsqlparser（pom 已加）。
 * @MapperScan 的 basePackages 不支持通配符，逐个列出本批的 mapper 包。
 */
@Configuration
@MapperScan(basePackages = {
        "com.trip.module.user.mapper",
        "com.trip.module.destination.mapper",
        "com.trip.module.route.mapper",
        "com.trip.module.interaction.mapper",
        "com.trip.module.plan.mapper"
})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}