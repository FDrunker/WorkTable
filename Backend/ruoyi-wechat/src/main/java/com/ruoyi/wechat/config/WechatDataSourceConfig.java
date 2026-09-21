package com.ruoyi.wechat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ruoyi.wechat.datasource.MockWechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.datasource.cli.CliWechatDataSource;

/**
 * 数据源装配。
 *
 * <p>通过 {@code wechat.datasource} 切换实现，默认 mock，
 * 保证<b>新环境拉下来就能跑通全链路</b>而不依赖 wechat-cli 是否装好。
 */
@Configuration
public class WechatDataSourceConfig
{
    private static final Logger log = LoggerFactory.getLogger(WechatDataSourceConfig.class);

    @Bean
    @ConditionalOnProperty(name = "wechat.datasource", havingValue = "cli")
    public WechatDataSource cliWechatDataSource(WechatProperties properties)
    {
        log.info("微信数据源：wechat-cli 命令行");
        return new CliWechatDataSource(properties);
    }

    /**
     * 兜底数据源：未显式配置或配置了未知值时使用内置样例，
     * 避免因为一个小配置笔误导致应用起不来。
     */
    @Bean
    @ConditionalOnProperty(name = "wechat.datasource", havingValue = "mock", matchIfMissing = true)
    public WechatDataSource mockWechatDataSource()
    {
        log.info("微信数据源：内置样例（mock）。如需真实数据请设置 wechat.datasource=cli");
        return new MockWechatDataSource();
    }
}
