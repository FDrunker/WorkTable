package com.ruoyi.wechat.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.ruoyi.wechat.datasource.MockWechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.datasource.text.TextWechatDataSource;

/**
 * {@link WechatDataSourceConfig} 装配测试。
 *
 * <p>数据来源改为读取文本之后，"默认到底装配了哪个数据源"成了关键配置：
 * 装错了表现为「导入文本」按钮点了没反应（因为注入不到 {@link TextWechatDataSource}），
 * 而这类问题不会在编译期暴露。
 */
class WechatDataSourceConfigTest
{
    @Test
    @DisplayName("未配置 wechat.datasource 时默认装配文本数据源")
    void shouldUseTextDataSourceByDefault()
    {
        try (AnnotationConfigApplicationContext context = newContext(null))
        {
            assertInstanceOf(TextWechatDataSource.class, context.getBean(WechatDataSource.class));
            assertNotNull(context.getBean(TextWechatDataSource.class));
        }
    }

    @Test
    @DisplayName("显式配置 text 时同样是文本数据源")
    void shouldUseTextDataSourceWhenConfigured()
    {
        try (AnnotationConfigApplicationContext context = newContext("text"))
        {
            assertInstanceOf(TextWechatDataSource.class, context.getBean(WechatDataSource.class));
        }
    }

    @Test
    @DisplayName("切到 mock 时不再装配文本数据源，导入入口据此提示配置错误")
    void shouldNotExposeTextDataSourceWhenDisabled()
    {
        try (AnnotationConfigApplicationContext context = newContext("mock"))
        {
            assertInstanceOf(MockWechatDataSource.class, context.getBean(WechatDataSource.class));
            assertThrows(NoSuchBeanDefinitionException.class,
                    () -> context.getBean(TextWechatDataSource.class));
        }
    }

    private AnnotationConfigApplicationContext newContext(String datasource)
    {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        StandardEnvironment environment = new StandardEnvironment();
        if (datasource != null)
        {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("wechat.datasource", datasource)));
        }
        context.setEnvironment(environment);
        context.register(WechatDataSourceConfig.class);
        context.refresh();
        return context;
    }
}
