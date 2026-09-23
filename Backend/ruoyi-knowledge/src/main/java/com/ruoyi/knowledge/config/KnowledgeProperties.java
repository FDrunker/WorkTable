package com.ruoyi.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库与模型管理配置。
 */
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties
{
    /** Markdown 暂存目录 */
    private String storagePath = "./knowledge-upload";

    /** API Key AES-GCM 主密钥，Base64 编码的 32 字节密钥 */
    private String encryptionKey;

    /** Chroma 连接配置 */
    private final Chroma chroma = new Chroma();

    /** 异步任务线程数 */
    private int taskThreads = 4;

    public String getStoragePath()
    {
        return storagePath;
    }

    public void setStoragePath(String storagePath)
    {
        this.storagePath = storagePath;
    }

    public String getEncryptionKey()
    {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey)
    {
        this.encryptionKey = encryptionKey;
    }

    public Chroma getChroma()
    {
        return chroma;
    }

    public int getTaskThreads()
    {
        return taskThreads;
    }

    public void setTaskThreads(int taskThreads)
    {
        this.taskThreads = taskThreads;
    }

    public static class Chroma
    {
        private String baseUrl = "http://localhost:8000";

        private String tenant = "default_tenant";

        private String database = "default_database";

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getTenant()
        {
            return tenant;
        }

        public void setTenant(String tenant)
        {
            this.tenant = tenant;
        }

        public String getDatabase()
        {
            return database;
        }

        public void setDatabase(String database)
        {
            this.database = database;
        }
    }
}
