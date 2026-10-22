package com.xiangcetong.ai.mapper;

import com.xiangcetong.ai.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 知识库分块与向量 Mapper。
 */
@Mapper
public interface KnowledgeChunkMapper {

    /** 批量插入分块 */
    int batchInsert(List<KnowledgeChunk> chunks);

    /** 查询所有分块向量（全量加载用于内存检索） */
    List<KnowledgeChunk> findAll();

    /** 按文档 ID 查询所有分块 */
    List<KnowledgeChunk> findByDocumentId(Long documentId);

    /** 按文档 ID 删除所有分块 */
    int deleteByDocumentId(Long documentId);

    /** 单条插入分块 */
    int insert(KnowledgeChunk chunk);

    /** 单条删除分块 */
    int deleteById(Long id);
}