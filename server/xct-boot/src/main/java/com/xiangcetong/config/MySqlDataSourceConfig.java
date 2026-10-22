package com.xiangcetong.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 显式装配 MySQL 主数据源（spring.datasource.*）并标记 @Primary。
 *
 * 2026-10-18 修复线上故障（RAG/pgvector 接入后的数据源劫持）：
 *   xct-ai 的 PgVectorConfig 注册了第二个 DataSource（ragDataSource）后，
 *   Spring Boot 的 DataSourceAutoConfiguration 因 @ConditionalOnMissingBean 整体退避，
 *   而 spring.datasource.* 此前只有配置、没有 Bean —— 于是：
 *     1. MyBatis 的 SqlSessionFactory / 事务管理器拿不到 MySQL 源；
 *     2. ragJdbcTemplate 又让 JdbcTemplateAutoConfiguration 退避，
 *        普通 JdbcTemplate 注入也解析不到 Bean。
 *   结果全库操作（含启动期各 Runner）都打到 pgvector 上，
 *   MySQL 方言 SQL（AUTO_INCREMENT、UPDATE ... INNER JOIN）在 PG 全部语法报错。
 *
 * 装配关系：
 *   - 本类提供 @Primary 的 MySQL DataSource + JdbcTemplate，
 *     MyBatis Mapper、业务 JdbcTemplate 全部走 MySQL 主库；
 *   - pgvector 数据源保持 @Qualifier("ragDataSource") / @Qualifier("ragJdbcTemplate")
 *     按名注入（见 xct-ai 的 PgVectorConfig / PgVectorChunkStore），两库互不干扰。
 */
@Configuration
public class MySqlDataSourceConfig {

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String driverClass) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClass);
        // spring.datasource.hikari.*（连接池大小/超时）绑定到该实例，
        // 保留 application-prod.yml 里为小内存机调好的池子参数
        return ds;
    }

    /**
     * 主 JdbcTemplate。ragJdbcTemplate 已占用了 JdbcTemplate 类型，
     * JdbcTemplateAutoConfiguration 因此退避，这里必须显式提供 @Primary 实例，
     * 否则按类型注入 JdbcTemplate 的地方（各启动 Runner / AgentService）会歧义报错。
     */
    @Bean(name = "jdbcTemplate")
    @Primary
    public JdbcTemplate primaryJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
