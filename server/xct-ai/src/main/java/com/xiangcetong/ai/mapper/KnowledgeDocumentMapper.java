package com.xiangcetong.ai.mapper;

import com.xiangcetong.ai.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 知识库文档 Mapper。
 */
@Mapper
public interface KnowledgeDocumentMapper {

    /** 分页查询文档列表 */
    List<KnowledgeDocument> list(int offset, int limit);

    /** 统计文档总数 */
    long count();

    /** 按 ID 查询 */
    KnowledgeDocument findById(Long id);

    /** 新增文档 */
    int insert(KnowledgeDocument doc);

    /** 更新文档状态 */
    int updateStatus(Long id, String status, String errorMsg, Integer chunkCount);

    /** 删除文档 */
    int deleteById(Long id);

    /** 按标题查询文档 */
    KnowledgeDocument findByTitle(String title);
}