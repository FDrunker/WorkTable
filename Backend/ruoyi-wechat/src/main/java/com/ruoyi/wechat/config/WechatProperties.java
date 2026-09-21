package com.ruoyi.wechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信模块配置，对应 application.yml 中的 {@code wechat.*}。
 */
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties
{
    /** 数据源类型：mock | cli | upload */
    private String datasource = "mock";

    /** 命令行数据源配置 */
    private Cli cli = new Cli();

    /** 清洗配置 */
    private Clean clean = new Clean();

    /** 军师 skill 配置 */
    private Skill skill = new Skill();

    /** LLM 配置 */
    private Llm llm = new Llm();

    public static class Cli
    {
        /**
         * wechat-cli 可执行文件的绝对路径。
         *
         * <p>因为安装时常常没有配置全局 PATH，这里推荐直接写绝对路径，例如
         * {@code E:/home/zuijiu/.pure-npm-global/wechat-cli.cmd}。
         * 留空时按 {@code WECHAT_CLI_BINARY} 环境变量 → PATH 的顺序自动探测。
         */
        private String executable = "";

        /** 单次命令超时（秒） */
        private int timeoutSeconds = 120;

        /** 单次请求消息条数上限。wechat-cli 源码中 _QUERY_LIMIT_MAX = 500 */
        private int batchSize = 500;

        /** 输出编码，留空表示自动探测（UTF-8 失败回退 GBK） */
        private String charset = "";

        public String getExecutable()
        {
            return executable;
        }

        public void setExecutable(String executable)
        {
            this.executable = executable;
        }

        public int getTimeoutSeconds()
        {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds)
        {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getBatchSize()
        {
            return batchSize;
        }

        public void setBatchSize(int batchSize)
        {
            this.batchSize = batchSize;
        }

        public String getCharset()
        {
            return charset;
        }

        public void setCharset(String charset)
        {
            this.charset = charset;
        }
    }

    public static class Clean
    {
        /** 是否丢弃表情/通话/撤回/系统等无分析价值消息 */
        private boolean dropNoise = true;

        /** 折叠连续同一人消息的阈值，超出的重复内容会被合并；<=1 表示不折叠 */
        private int collapseRepeatThreshold = 5;

        /** 是否对手机号/身份证/银行卡做脱敏。发给云端 API 前建议保持开启 */
        private boolean maskSensitive = true;

        /** 送入 LLM 的上下文字符预算 */
        private int contextCharBudget = 40000;

        /** 单次分析最多处理多少条未分析消息 */
        private int maxUnanalyzedPerAnalysis = 500;

        /** 前端聊天记录每页条数 */
        private int messagePageSize = 50;

        public int getMaxUnanalyzedPerAnalysis()
        {
            return maxUnanalyzedPerAnalysis;
        }

        public void setMaxUnanalyzedPerAnalysis(int maxUnanalyzedPerAnalysis)
        {
            this.maxUnanalyzedPerAnalysis = maxUnanalyzedPerAnalysis;
        }

        public int getMessagePageSize()
        {
            return messagePageSize;
        }

        public void setMessagePageSize(int messagePageSize)
        {
            this.messagePageSize = messagePageSize;
        }

        public boolean isDropNoise()
        {
            return dropNoise;
        }

        public void setDropNoise(boolean dropNoise)
        {
            this.dropNoise = dropNoise;
        }

        public int getCollapseRepeatThreshold()
        {
            return collapseRepeatThreshold;
        }

        public void setCollapseRepeatThreshold(int collapseRepeatThreshold)
        {
            this.collapseRepeatThreshold = collapseRepeatThreshold;
        }

        public boolean isMaskSensitive()
        {
            return maskSensitive;
        }

        public void setMaskSensitive(boolean maskSensitive)
        {
            this.maskSensitive = maskSensitive;
        }

        public int getContextCharBudget()
        {
            return contextCharBudget;
        }

        public void setContextCharBudget(int contextCharBudget)
        {
            this.contextCharBudget = contextCharBudget;
        }
    }

    public static class Skill
    {
        /** classpath 下 skill 资源根目录 */
        private String location = "classpath:skill/";

        /** 单次分析最多加载几篇参考文档 */
        private int maxReferences = 3;

        public String getLocation()
        {
            return location;
        }

        public void setLocation(String location)
        {
            this.location = location;
        }

        public int getMaxReferences()
        {
            return maxReferences;
        }

        public void setMaxReferences(int maxReferences)
        {
            this.maxReferences = maxReferences;
        }
    }

    public static class Llm
    {
        /** OpenAI 兼容接口的 base url，例如 https://api.deepseek.com */
        private String baseUrl = "";

        /** API Key。建议用环境变量注入，不要提交到仓库 */
        private String apiKey = "";

        /** 模型名 */
        private String model = "deepseek-chat";

        private double temperature = 0.7;

        private int maxTokens = 4096;

        /** 建立连接超时（毫秒） */
        private int connectTimeoutMs = 10000;

        /** 读取超时（毫秒）。长文分析很容易超过默认值 */
        private int readTimeoutMs = 180000;

        /** 失败重试次数（仅对 429 / 5xx 生效，不对 4xx 重试） */
        private int maxRetries = 2;

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getApiKey()
        {
            return apiKey;
        }

        public void setApiKey(String apiKey)
        {
            this.apiKey = apiKey;
        }

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
        }

        public double getTemperature()
        {
            return temperature;
        }

        public void setTemperature(double temperature)
        {
            this.temperature = temperature;
        }

        public int getMaxTokens()
        {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens)
        {
            this.maxTokens = maxTokens;
        }

        public int getConnectTimeoutMs()
        {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs)
        {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs()
        {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs)
        {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getMaxRetries()
        {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries)
        {
            this.maxRetries = maxRetries;
        }
    }

    public String getDatasource()
    {
        return datasource;
    }

    public void setDatasource(String datasource)
    {
        this.datasource = datasource;
    }

    public Cli getCli()
    {
        return cli;
    }

    public void setCli(Cli cli)
    {
        this.cli = cli;
    }

    public Clean getClean()
    {
        return clean;
    }

    public void setClean(Clean clean)
    {
        this.clean = clean;
    }

    public Skill getSkill()
    {
        return skill;
    }

    public void setSkill(Skill skill)
    {
        this.skill = skill;
    }

    public Llm getLlm()
    {
        return llm;
    }

    public void setLlm(Llm llm)
    {
        this.llm = llm;
    }
}
