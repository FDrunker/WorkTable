package com.ruoyi.wechat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ruoyi.wechat.datasource.MockWechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.datasource.cli.CliWechatDataSource;
import com.ruoyi.wechat.datasource.text.TextWechatDataSource;

/**
 * 数据源装配。
 *
 * <p>通过 {@code wechat.datasource} 切换实现，<b>默认 text</b>：
 * 聊天分析的数据来源是用户粘贴的聊天记录文本，不依赖任何外部环境。
 * {@code mock}（内置样例）与 {@code cli}（本地 wechat-cli）作为可选来源保留。
 *
 * <p>注意每个分支都返回<b>具体类型</b>：文本导入入口需要拿到
 * {@link TextWechatDataSource} 本身，只声明接口会让按类型注入拿不到它。
 */
@Configuration
public class WechatDataSourceConfig
{
    private static final Logger log = LoggerFactory.getLogger(WechatDataSourceConfig.class);

    /**
     * 默认数据源：粘贴文本。
     */
    @Bean
    @ConditionalOnProperty(name = "wechat.datasource", havingValue = "text", matchIfMissing = true)
    public TextWechatDataSource textWechatDataSource()
    {
        log.info("微信数据源：粘贴文本（默认）");
        return new TextWechatDataSource();
    }

    @Bean
    @ConditionalOnProperty(name = "wechat.datasource", havingValue = "cli")
    public WechatDataSource cliWechatDataSource(WechatProperties properties)
    {
        log.info("微信数据源：wechat-cli 命令行");
        return new CliWechatDataSource(properties);
    }

    /**
     * 内置样例数据源，仅用于演示与联调。
     */
    @Bean
    @ConditionalOnProperty(name = "wechat.datasource", havingValue = "mock")
    public WechatDataSource mockWechatDataSource()
    {
        log.info("微信数据源：内置样例（mock）");
        return new MockWechatDataSource();
    }
}
