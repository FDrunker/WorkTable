package com.ruoyi.web.controller.wechat;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wechat.datasource.WechatDataSourceException;
import com.ruoyi.wechat.llm.LlmException;
import com.ruoyi.wechat.service.IdentityRequiredException;

/**
 * 微信模块异常处理。
 *
 * <p>把内部异常翻译成"用户能照着做点什么"的响应。
 * 尤其是数据源错误：它附带 {@code suggestion}，
 * 直接展示比让用户去翻日志有效得多。
 *
 * <p>用 {@code basePackages} 限定作用范围，避免影响其他模块的错误处理。
 */
@RestControllerAdvice(basePackages = "com.ruoyi.web.controller.wechat")
public class WechatExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(WechatExceptionHandler.class);

    /** 需要在用户确认说话人映射后继续 */
    private static final int CODE_IDENTITY_REQUIRED = 4001;

    /** 数据源不可用或取数失败 */
    private static final int CODE_DATASOURCE = 4002;

    /** LLM 调用失败 */
    private static final int CODE_LLM = 4003;

    @ExceptionHandler(IdentityRequiredException.class)
    public AjaxResult handleIdentityRequired(IdentityRequiredException e)
    {
        AjaxResult result = AjaxResult.error(CODE_IDENTITY_REQUIRED, e.getMessage());
        result.put("candidate", e.getCandidate());
        return result;
    }

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

    @ExceptionHandler(LlmException.class)
    public AjaxResult handleLlm(LlmException e)
    {
        log.warn("LLM 调用错误：{}", e.getMessage());
        Map<String, Object> extra = new HashMap<>();
        extra.put("retryable", e.retryable());
        AjaxResult result = AjaxResult.error(CODE_LLM, e.getMessage());
        result.putAll(extra);
        return result;
    }

    @ExceptionHandler(IllegalStateException.class)
    public AjaxResult handleIllegalState(IllegalStateException e)
    {
        log.warn("微信分析失败：{}", e.getMessage());
        return AjaxResult.error(e.getMessage());
    }
}
