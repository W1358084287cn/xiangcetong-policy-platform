package com.xiangcetong.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * pgvector 向量库数据源配置（RAG 检索增强专用）。
 * 与现有 MySQL 主数据源并存：MySQL 仍是业务主库，pgvector 仅承载知识库向量检索。
 * 连接信息由 docker-compose 注入的环境变量 PG_HOST / PG_PORT / PG_DB / PG_USER / PG_PASSWORD 提供，
 * 不写在配置文件中，避免明文密码散落。
 */
@Configuration
public class PgVectorConfig {

    @Value("${PG_HOST:pgvector}")
    private String host;
    @Value("${PG_PORT:5432}")
    private String port;
    @Value("${PG_DB:xiangcetong_rag}")
    private String db;
    @Value("${PG_USER:xct_rag}")
    private String user;
    @Value("${PG_PASSWORD:}")
    private String password;

    @Bean(name = "ragDataSource")
    public DataSource ragDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        // 向量检索并发极低，小池子即可，控制内存占用（服务器仅 2G）
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setPoolName("rag-pg-pool");
        ds.setConnectionTimeout(10000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        return ds;
    }

    @Bean(name = "ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate(@Qualifier("ragDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
