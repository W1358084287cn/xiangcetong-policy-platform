package com.xiangcetong.ai.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * pgvector 向量分块存储：替代原 MySQL knowledge_chunk 的向量检索职责。
 *
 * 检索使用 pgvector 原生的余弦距离算子（embedding <=> :q），由数据库完成近邻排序，
 * 不再把全表读进 JVM 用 Java 算余弦（原实现每问全表扫描 + 内存比对，无法扩展）。
 *
 * 表 knowledge_chunk_pg 由 docker 初始化 SQL 创建：
 *   id, document_id, document_title, chunk_index, chunk_text,
 *   embedding vector(1024), policy_id, category, region, content_hash, token_count
 */
@Repository
public class PgVectorChunkStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorChunkStore.class);

    private final JdbcTemplate jdbc;

    public PgVectorChunkStore(@Qualifier("ragJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一个知识分块（向量以 float[] 形式仅在写入时临时存在） */
    public static class ChunkRow {
        public Long id;
        public Long documentId;
        public String documentTitle;
        public int chunkIndex;
        public String chunkText;
        public Long policyId;
        public String category;
        public String region;
        public String contentHash;
        public int tokenCount;
        public double score;
        public float[] embedding;

        public ChunkRow() {
        }

        public ChunkRow(Long documentId, String documentTitle, int chunkIndex, String chunkText,
                        Long policyId, String category, String region, String contentHash, int tokenCount) {
            this.documentId = documentId;
            this.documentTitle = documentTitle;
            this.chunkIndex = chunkIndex;
            this.chunkText = chunkText;
            this.policyId = policyId;
            this.category = category;
            this.region = region;
            this.contentHash = contentHash;
            this.tokenCount = ***;
        }
    }

    private static final RowMapper<ChunkRow> ROW_MAPPER = (rs, i) -> {
        ChunkRow r = new ChunkRow();
        r.id = rs.getLong("id");
        r.documentId = rs.getLong("document_id");
        r.documentTitle = rs.getString("document_title");
        r.chunkIndex = rs.getInt("chunk_index");
        r.chunkText = rs.getString("chunk_text");
        long pid = rs.getLong("policy_id");
        r.policyId = rs.wasNull() ? null : pid;
        r.category = rs.getString("category");
        r.region = rs.getString("region");
        r.contentHash = rs.getString("content_hash");
        r.tokenCount = ***("token_count");
        try {
            double s = rs.getDouble("score");
            r.score = rs.wasNull() ? 0.0 : s;
        } catch (SQLException ignore) {
            r.score = 0.0;
        }
        return r;
    };

    /** float[] -> pgvector 字面量 '[v0,v1,...]' */
    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /** 批量写入分块（embedding 以 vector 字面量写入） */
    public void saveBatch(List<ChunkRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        String sql = "INSERT INTO knowledge_chunk_pg "
                + "(document_id, document_title, chunk_index, chunk_text, embedding, policy_id, category, region, content_hash, token_count) "
                + "VALUES (?,?,?,?,?::vector,?,?,?,?,?)";
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public int getBatchSize() {
                return rows.size();
            }

            @Override
            public void setValues(PreparedStatement ps, int idx) throws SQLException {
                ChunkRow r = rows.get(idx);
                ps.setLong(1, r.documentId);
                ps.setString(2, r.documentTitle);
                ps.setInt(3, r.chunkIndex);
                ps.setString(4, r.chunkText);
                ps.setString(5, toVectorLiteral(r.embedding));
                if (r.policyId != null) ps.setLong(6, r.policyId);
                else ps.setNull(6, Types.BIGINT);
                if (r.category != null) ps.setString(7, r.category);
                else ps.setNull(7, Types.VARCHAR);
                if (r.region != null) ps.setString(8, r.region);
                else ps.setNull(8, Types.VARCHAR);
                if (r.contentHash != null) ps.setString(9, r.contentHash);
                else ps.setNull(9, Types.VARCHAR);
                ps.setInt(10, r.tokenCount);
            }
        });
    }

    /** 余弦近邻检索：返回 score = 1 - cosine_distance（越大越相似），按相似度降序，最多 k 条 */
    public List<ChunkRow> search(float[] query, int k) {
        String lit = toVectorLiteral(query);
        String sql = "SELECT id, document_id, document_title, chunk_text, chunk_index, "
                + "policy_id, category, region, content_hash, token_count, "
                + "1 - (embedding <=> ?::vector) AS score "
                + "FROM knowledge_chunk_pg "
                + "WHERE embedding IS NOT NULL "
                + "ORDER BY embedding <=> ?::vector LIMIT ?";
        return jdbc.query(sql, ROW_MAPPER, lit, lit, k);
    }

    /** 全量读取（仅供关键词兜底检索使用，规模小） */
    public List<ChunkRow> findAll() {
        String sql = "SELECT id, document_id, document_title, chunk_text, chunk_index, "
                + "policy_id, category, region, content_hash, token_count FROM knowledge_chunk_pg";
        return jdbc.query(sql, ROW_MAPPER);
    }

    public ChunkRow findById(Long id) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, document_id, document_title, chunk_text, chunk_index, "
                            + "policy_id, category, region, content_hash, token_count FROM knowledge_chunk_pg WHERE id=?",
                    ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public ChunkRow findByDocAndIndex(Long documentId, int chunkIndex) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, document_id, document_title, chunk_text, chunk_index, "
                            + "policy_id, category, region, content_hash, token_count FROM knowledge_chunk_pg "
                            + "WHERE document_id=? AND chunk_index=?",
                    ROW_MAPPER, documentId, chunkIndex);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int deleteByDocumentId(Long documentId) {
        return jdbc.update("DELETE FROM knowledge_chunk_pg WHERE document_id=?", documentId);
    }

    /** 仅删除某文档下标题前缀匹配的分块（用于单条政策增量同步） */
    public int deleteByDocumentIdAndTitlePrefix(Long documentId, String prefix) {
        return jdbc.update("DELETE FROM knowledge_chunk_pg WHERE document_id=? AND chunk_text LIKE ?",
                documentId, prefix + "%");
    }

    /** 有效向量分块数（embedding 非空），用于启动自检 */
    public int count() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunk_pg WHERE embedding IS NOT NULL", Integer.class);
        return n == null ? 0 : n;
    }

    public int deleteAll() {
        return jdbc.update("DELETE FROM knowledge_chunk_pg");
    }
}
