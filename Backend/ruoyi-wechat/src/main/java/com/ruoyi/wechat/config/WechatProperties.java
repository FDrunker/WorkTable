package com.ruoyi.wechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信模块配置，对应 application.yml 中的 {@code wechat.*}。
 *
 * <p>调用 agent 的能力（LLM 客户端、军师知识库）已整体移除，
 * 因此这里不再有 {@code llm.*} 与 {@code skill.*} 配置项。
 */
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties
{
    /** 数据源类型：mock | cli */
    private String datasource = "mock";

    /** 命令行数据源配置 */
    private Cli cli = new Cli();

    /** 清洗配置 */
    private Clean clean = new Clean();

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

        /** 是否对手机号/身份证/银行卡做脱敏 */
        private boolean maskSensitive = true;

        /** 单条消息渲染时的上下文字符预算 */
        private int contextCharBudget = 40000;

        /** 前端聊天记录每页条数 */
        private int messagePageSize = 50;

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

        public int getMessagePageSize()
        {
            return messagePageSize;
        }

        public void setMessagePageSize(int messagePageSize)
        {
            this.messagePageSize = messagePageSize;
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
}
