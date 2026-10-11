package com.medifood.common.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 工具类 (适配 8.13.2 版本)
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class ElasticsearchUtil {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    /**
     * 创建或更新文档
     *
     * @param index 索引名称
     * @param id    文档ID
     * @param source 文档内容
     * @return 是否成功
     */
    public boolean indexDocument(String index, String id, Map<String, Object> source) {
        try {
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(index)
                    .id(id)
                    .document(source)
            );

            IndexResponse response = elasticsearchClient.index(request);
            return response.result() != null;
        } catch (IOException e) {
            log.error("Elasticsearch 创建文档失败，index: {}, id: {}", index, id, e);
            return false;
        } catch (Exception e) {
            log.error("Elasticsearch 创建文档失败，index: {}, id: {}", index, id, e);
            return false;
        }
    }

    /**
     * 删除文档
     *
     * @param index 索引名称
     * @param id    文档ID
     * @return 是否成功
     */
    public boolean deleteDocument(String index, String id) {
        try {
            DeleteRequest request = DeleteRequest.of(d -> d
                    .index(index)
                    .id(id)
            );

            DeleteResponse response = elasticsearchClient.delete(request);
            return response.result() != null;
        } catch (IOException e) {
            log.error("Elasticsearch 删除文档失败，index: {}, id: {}", index, id, e);
            return false;
        } catch (Exception e) {
            log.error("Elasticsearch 删除文档失败，index: {}, id: {}", index, id, e);
            return false;
        }
    }

    /**
     * 按字段值删除文档
     *
     * @param index 索引名称
     * @param field 字段名
     * @param value 字段值
     * @return 是否成功
     */
    public boolean deleteByQuery(String index, String field, Object value) {
        try {
            DeleteByQueryResponse response = elasticsearchClient.deleteByQuery(d -> d
                    .index(index)
                    .query(q -> q
                            .term(t -> t
                                    .field(field)
                                    .value(toFieldValue(value))
                            )
                    )
            );
            return response.deleted() >= 0;
        } catch (Exception e) {
            if (isIndexNotFound(e)) {
                log.debug("Elasticsearch 索引不存在（删除跳过），index: {}", index);
            } else {
                log.error("Elasticsearch 按条件删除文档失败，index: {}, field: {}, value: {}", index, field, value, e);
            }
            return false;
        }
    }

    /**
     * 搜索文档 (支持多字段)
     *
     * @param index   索引名称
     * @param keyword 关键词
     * @param from    起始位置
     * @param size    每页大小
     * @param fields  搜索字段列表
     * @return 搜索结果
     */
    public List<Map<String, Object>> search(String index, String keyword, int from, int size, String... fields) {
        return searchWithTotal(index, keyword, from, size, fields).getRecords();
    }

    /**
     * 搜索结果封装类
     */
    public static class SearchResult {
        private List<Map<String, Object>> records = new ArrayList<>();
        private long total = 0;

        public List<Map<String, Object>> getRecords() {
            return records;
        }

        public void setRecords(List<Map<String, Object>> records) {
            this.records = records;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }
    }

    /**
     * 搜索文档并返回总数
     */
    public SearchResult searchWithTotal(String index, String keyword, int from, int size, String... fields) {
        SearchResult result = new SearchResult();
        try {
            SearchResponse<Map> response;

            if (keyword != null && !keyword.isEmpty()) {
                response = elasticsearchClient.search(s -> s
                                .index(index)
                                .query(q -> q
                                        .multiMatch(m -> m
                                                .query(keyword)
                                                .fields(List.of(fields))
                                        )
                                )
                                .from(from)
                                .size(size)
                        , Map.class);
            } else {
                response = elasticsearchClient.search(s -> s
                                .index(index)
                                .query(q -> q.matchAll(m -> m))
                                .from(from)
                                .size(size)
                        , Map.class);
            }

            result.setTotal(response.hits().total() != null ? response.hits().total().value() : 0);
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    source.put("_score", hit.score() != null ? hit.score() : 0.0);
                    result.getRecords().add(source);
                }
            }
        } catch (Exception e) {
            if (isIndexNotFound(e)) {
                log.debug("Elasticsearch 索引不存在，index: {}", index);
            } else {
                log.error("Elasticsearch 搜索失败，index: {}, fields: {}, keyword: {}", index, fields, keyword, e);
            }
        }
        return result;
    }

    /**
     * 创建基于 HNSW 的向量检索索引（dense_vector）
     *
     * @param index 索引名称
     * @param dims  向量维度
     */
    public boolean createKnnIndex(String index, int dims) {
        try {
            var createIndexResponse = elasticsearchClient.indices().create(c -> c
                    .index(index)
                    .mappings(m -> m
                            .properties("id", p -> p.keyword(k -> k))
                            .properties("vector", p -> p
                                    .denseVector(v -> v
                                            .dims(dims)
                                            .index(true)
                                            .similarity("cosine")
                                    )
                            )
                            .properties("content", p -> p.text(t -> t))
                            .properties("title", p -> p.text(t -> t))
                    )
            );
            return createIndexResponse.acknowledged();
        } catch (Exception e) {
            log.warn("创建 KNN 索引失败或已存在，index={}", index, e);
            return false;
        }
    }

    /**
     * 检测索引维度是否匹配，不匹配时删除旧索引并重建
     * 解决因 Embedding 模型变更导致的向量维度不一致问题
     *
     * @param index 索引名称
     * @param dims  期望的向量维度
     */
    public boolean recreateKnnIndexIfDimsMismatch(String index, int dims) {
        try {
            // 检查索引是否存在
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(index)).value();
            if (!exists) {
                log.info("索引 {} 不存在，直接创建 (dims={})", index, dims);
                return createKnnIndex(index, dims);
            }
            // 获取当前索引的 mapping，检查 vector 字段维度
            var mapping = elasticsearchClient.indices().getMapping(m -> m.index(index));
            var props = mapping.get(index).mappings().properties();
            if (props.containsKey("vector") && props.get("vector").isDenseVector()) {
                var denseVector = props.get("vector").denseVector();
                Integer currentDims = denseVector.dims();
                if (currentDims != null && currentDims == dims) {
                    log.debug("索引 {} 维度匹配: {} == {}", index, currentDims, dims);
                    return true;
                }
                log.warn("索引 {} 维度不匹配: 当前={}, 期望={}, 删除旧索引重建", index, currentDims, dims);
            }
            // 删除旧索引并重建
            elasticsearchClient.indices().delete(d -> d.index(index));
            log.info("已删除旧索引 {}，重建中 (dims={})", index, dims);
            return createKnnIndex(index, dims);
        } catch (Exception e) {
            log.error("重建 KNN 索引失败，index={}, dims={}", index, dims, e);
            return false;
        }
    }

    /**
     * 写入（或覆盖）带向量的文档
     */
    public boolean indexDocumentWithVector(String index, String id, Map<String, Object> source, float[] vector) {
        try {
            Map<String, Object> doc = new HashMap<>(source);
            List<Float> vecList = new ArrayList<>(vector.length);
            for (float v : vector) vecList.add(v);
            doc.put("vector", vecList);
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(index)
                    .id(id)
                    .document(doc)
            );
            IndexResponse response = elasticsearchClient.index(request);
            return response.result() != null;
        } catch (Exception e) {
            log.error("Elasticsearch 写入向量文档失败，index: {}, id: {}", index, id, e);
            return false;
        }
    }

    /**
     * 基于 HNSW 的向量检索（KNN）
     *
     * @param index 索引
     * @param queryVector 查询向量
     * @param k 返回 TopK
     * @param numCandidates HNSW 候选数
     */
    public SearchResult knnSearch(String index, float[] queryVector, int k, int numCandidates, String... sourceIncludes) {
        SearchResult result = new SearchResult();
        try {
            List<Float> qVec = new ArrayList<>(queryVector.length);
            for (float v : queryVector) qVec.add(v);

            KnnQuery knn = KnnQuery.of(knnBuilder -> knnBuilder
                    .field("vector")
                    .queryVector(qVec)
                    .k(k)
                    .numCandidates(numCandidates)
            );

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(index)
                            .knn(knn)
                            .source(src -> src.filter(f -> f.includes(List.of(sourceIncludes))))
                            .size(k),
                    Map.class);

            result.setTotal(response.hits().total() != null ? response.hits().total().value() : 0);
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    result.getRecords().add(source);
                }
            }
        } catch (Exception e) {
            if (isIndexNotFound(e)) {
                log.debug("Elasticsearch KNN 索引不存在，index: {}", index);
            } else {
                log.error("Elasticsearch KNN 搜索失败，index: {}", index, e);
            }
        }
        return result;
    }

    /**
     * 根据文档 ID 获取完整文档
     *
     * @param index  索引名称
     * @param id     文档 ID
     * @param fields 返回的字段（不传则返回全部字段）
     * @return 文档内容，不存在或出错返回 null
     */
    public Map<String, Object> getDocumentById(String index, String id, String... fields) {
        try {
            GetResponse<Map> response;
            if (fields != null && fields.length > 0) {
                response = elasticsearchClient.get(g -> g
                        .index(index)
                        .id(id)
                        .sourceIncludes(List.of(fields)), Map.class);
            } else {
                response = elasticsearchClient.get(g -> g
                        .index(index)
                        .id(id), Map.class);
            }
            return response.source();
        } catch (Exception e) {
            if (isIndexNotFound(e)) {
                log.debug("索引不存在，index: {}, id: {}", index, id);
            } else if (e.getMessage() != null && e.getMessage().contains("404")) {
                log.debug("文档不存在，index: {}, id: {}", index, id);
            } else {
                log.error("获取文档失败，index: {}, id: {}", index, id, e);
            }
            return null;
        }
    }

    /**
     * 搜索文档 (单字段兼容旧方法)
     *
     * @param index   索引名称
     * @param field   搜索字段
     * @param keyword 关键词
     * @param from    起始位置
     * @param size    每页大小
     * @return 搜索结果
     */
    public List<Map<String, Object>> search(String index, String field, String keyword, int from, int size) {
        return search(index, keyword, from, size, field);
    }

    /**
     * 判断 ES 异常是否由索引不存在引起
     * 多索引 RAG 场景下，部分索引不存在是正常情况，不应打印 ERROR
     */
    private boolean isIndexNotFound(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("index_not_found_exception");
    }

    private FieldValue toFieldValue(Object value) {
        if (value instanceof Number number) {
            if (number instanceof Integer i) {
                return FieldValue.of(i);
            }
            if (number instanceof Long l) {
                return FieldValue.of(l);
            }
            if (number instanceof Float f) {
                return FieldValue.of(f.doubleValue());
            }
            if (number instanceof Double d) {
                return FieldValue.of(d);
            }
            return FieldValue.of(number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return FieldValue.of(bool);
        }
        return FieldValue.of(String.valueOf(value));
    }
}
