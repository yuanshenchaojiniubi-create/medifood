package com.medifoodai.service;

public interface EmbeddingService {

    /**
     * 文档向量化 —— 知识/食谱存入向量库时调用
     * BGE 模型不加查询前缀，保持 passage 编码模式
     */
    float[] embed(String text);

    /**
     * 查询向量化 —— 用户 RAG 检索时调用
     * BGE 模型需加查询前缀，切换到 query 编码模式
     */
    default float[] embedForQuery(String text) {
        return embed(text);
    }

    int dimensions();

    String modelName();
}