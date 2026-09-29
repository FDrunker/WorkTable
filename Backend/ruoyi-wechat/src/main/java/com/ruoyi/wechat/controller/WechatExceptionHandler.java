package com.ruoyi.wechat.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wechat.datasource.WechatDataSourceException;

/**
 * 微信模块异常处理。
 *
 * <p>把内部异常翻译成"用户能照着做点什么"的响应。
 * 尤其是数据源错误：它附带 {@code suggestion}，直接展示比让用户去翻日志有效得多。
 *
 * <p>调用 agent 的能力已移除，因此这里不再有 LLM 相关的异常处理。
 *
 * <p>用 {@code basePackages} 限定作用范围，避免影响其他模块的错误处理。
 */
@RestControllerAdvice(basePackages = "com.ruoyi.wechat.controller")
public class WechatExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(WechatExceptionHandler.class);

    /** 数据源不可用或取数失败 */
    private static final int CODE_DATASOURCE = 4002;

    @ExceptionHandler(WechatDataSourceException.class)
    public AjaxResult handleDataSource(WechatDataSourceException e)
    {
        log.warn("微信数据源错误：{}", e.getMessage());
        AjaxResult result = AjaxResult.error(CODE_DATASOURCE, e.getMessage());
        if (e.getSuggestion() != null)
        {
            result.put("suggestion", e.getSuggestion());
        }
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult handleIllegalArgument(IllegalArgumentException e)
    {
        // 例如"会话不存在""无权访问该会话"
        log.warn("微信接口参数错误：{}", e.getMessage());
        return AjaxResult.error(e.getMessage());
    }
}
