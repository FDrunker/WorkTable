package com.ruoyi.knowledge.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 知识库模块基础 Bean。
 */
@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeConfig
{
    @Bean
    public ChromaApi chromaApi(KnowledgeProperties properties)
    {
        return ChromaApi.builder()
                .baseUrl(properties.getChroma().getBaseUrl())
                .build();
    }

    @Bean(name = "knowledgeTaskExecutor")
    public ThreadPoolTaskExecutor knowledgeTaskExecutor(KnowledgeProperties properties)
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, properties.getTaskThreads()));
        executor.setMaxPoolSize(Math.max(1, properties.getTaskThreads()));
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("knowledge-task-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
