package com.nuist.agent.job.config;

import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Milvus 向量库配置（成员 A：AI 基础设施）。
 *
 * <p>为什么不用官方自动配置：{@code MilvusVectorStoreAutoConfiguration} 会在应用启动时就创建
 * {@link MilvusServiceClient} 并建立 gRPC 连接，一旦 Milvus 未启动/网络不通，整个应用启动失败。
 * 这里排除官方自动配置，改为声明 <b>@Lazy</b> 的客户端与向量库 Bean：
 * 只有第一次真正做向量写入/检索时才连接 Milvus，Milvus 不可用时业务自动跳过 RAG 并降级。</p>
 *
 * <p>相关配置：{@code spring.ai.vectorstore.milvus.*}（连接与集合）、{@code job.vector.*}（检索策略）。</p>
 */
@Configuration
@EnableConfigurationProperties({MilvusServiceClientProperties.class, MilvusVectorStoreProperties.class,
        JobVectorProperties.class})
public class JobVectorConfig {

    /** 懒加载的 Milvus gRPC 客户端 */
    @Bean
    @Lazy
    public MilvusServiceClient milvusClient(MilvusVectorStoreProperties serverProperties,
                                            MilvusServiceClientProperties clientProperties) {
        var builder = ConnectParam.newBuilder()
                .withHost(clientProperties.getHost())
                .withPort(clientProperties.getPort())
                .withDatabaseName(serverProperties.getDatabaseName())
                .withConnectTimeout(clientProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .withKeepAliveTime(clientProperties.getKeepAliveTimeMs(), TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(clientProperties.getKeepAliveTimeoutMs(), TimeUnit.MILLISECONDS)
                .withRpcDeadline(clientProperties.getRpcDeadlineMs(), TimeUnit.MILLISECONDS)
                .withSecure(clientProperties.isSecure())
                .withIdleTimeout(clientProperties.getIdleTimeoutMs(), TimeUnit.MILLISECONDS)
                .withAuthorization(clientProperties.getUsername(), clientProperties.getPassword());

        if (clientProperties.isSecure()) {
            PropertyMapper mapper = PropertyMapper.get();
            mapper.from(clientProperties::getUri).whenHasText().to(builder::withUri);
            mapper.from(clientProperties::getToken).whenHasText().to(builder::withToken);
            mapper.from(clientProperties::getClientKeyPath).whenHasText().to(builder::withClientKeyPath);
            mapper.from(clientProperties::getClientPemPath).whenHasText().to(builder::withClientPemPath);
            mapper.from(clientProperties::getCaPemPath).whenHasText().to(builder::withCaPemPath);
            mapper.from(clientProperties::getServerPemPath).whenHasText().to(builder::withServerPemPath);
            mapper.from(clientProperties::getServerName).whenHasText().to(builder::withServerName);
        }

        return new MilvusServiceClient(builder.build());
    }

    /** 向量化批处理策略（按 token 数分批，避免单次 embedding 请求过大） */
    @Bean
    public BatchingStrategy milvusBatchingStrategy() {
        return new TokenCountBatchingStrategy();
    }

    /** 懒加载的 Milvus 向量库：注入到业务层的是延迟代理，首次使用时才连 Milvus */
    @Bean
    @Lazy
    @Primary
    public MilvusVectorStore jobVectorStore(MilvusServiceClient milvusClient,
                                            EmbeddingModel embeddingModel,
                                            MilvusVectorStoreProperties properties,
                                            BatchingStrategy milvusBatchingStrategy) {
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .embeddingDimension(properties.getEmbeddingDimension())
                .indexType(IndexType.valueOf(properties.getIndexType().name()))
                .metricType(MetricType.valueOf(properties.getMetricType().name()))
                .indexParameters(properties.getIndexParameters())
                .iDFieldName(properties.getIdFieldName())
                .autoId(properties.isAutoId())
                .contentFieldName(properties.getContentFieldName())
                .metadataFieldName(properties.getMetadataFieldName())
                .embeddingFieldName(properties.getEmbeddingFieldName())
                .batchingStrategy(milvusBatchingStrategy)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }
}
