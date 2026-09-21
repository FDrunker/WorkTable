package com.ruoyi.wechat.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.wechat.config.WechatProperties;

/**
 * OpenAI 兼容协议的 LLM 客户端。
 *
 * <p>之所以只做"OpenAI 兼容"这一种协议：DeepSeek、通义、智谱、Moonshot、
 * 本地 Ollama / vLLM 都提供 {@code /v1/chat/completions}，一个实现覆盖全部，
 * 不需要为每家引一个 SDK，也就不会把它们塞进依赖树里。
 *
 * <p>三个必须做对的点：
 * <ol>
 *   <li><b>超时</b>：长文分析的响应时间远超默认值，必须显式设置读超时；</li>
 *   <li><b>重试边界</b>：只重试 429 与 5xx，<b>绝不重试 4xx</b>
 *       —— 参数错误重试一百次还是错，只会浪费配额；</li>
 *   <li><b>成本记账</b>：把 usage 回传出去，否则线上跑起来不知道钱花在哪。</li>
 * </ol>
 */
@Component
public class LlmClient
{
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final WechatProperties properties;

    private volatile org.springframework.web.client.RestClient restClient;

    public LlmClient(WechatProperties properties)
    {
        this.properties = properties;
    }

    /**
     * 配置是否完整，用于前端给出明确提示。
     */
    public boolean isConfigured()
    {
        return properties.getLlm().getBaseUrl() != null && !properties.getLlm().getBaseUrl().isBlank()
                && properties.getLlm().getApiKey() != null && !properties.getLlm().getApiKey().isBlank();
    }

    /**
     * 发起一次对话补全。
     *
     * @param messages 消息序列，第一条通常是 system
     */
    public LlmResult chat(List<LlmMessage> messages)
    {
        if (!isConfigured())
        {
            throw new LlmException("LLM 未配置：请在 application.yml 中设置 wechat.llm.base-url 与 wechat.llm.api-key"
                    + "（也可用环境变量覆盖）");
        }

        WechatProperties.Llm cfg = properties.getLlm();
        JSONObject body = buildBody(messages, cfg);

        int attempts = Math.max(1, cfg.getMaxRetries() + 1);
        LlmException last = null;
        for (int i = 1; i <= attempts; i++)
        {
            try
            {
                return doCall(body, cfg);
            }
            catch (LlmException e)
            {
                last = e;
                if (!e.retryable() || i == attempts)
                {
                    throw e;
                }
                long backoff = (long) Math.pow(2, i) * 500L;
                log.warn("LLM 调用失败（第 {}/{} 次，HTTP {}），{}ms 后重试：{}",
                        i, attempts, e.getHttpStatus(), backoff, e.getMessage());
                sleep(backoff);
            }
        }
        throw last == null ? new LlmException("LLM 调用失败") : last;
    }

    private LlmResult doCall(JSONObject body, WechatProperties.Llm cfg)
    {
        long start = System.currentTimeMillis();
        String raw;
        try
        {
            raw = getRestClient(cfg).post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body.toJSONString())
                    .retrieve()
                    .body(String.class);
        }
        catch (org.springframework.web.client.RestClientResponseException e)
        {
            // 把服务端返回的错误体带出来，否则只有一句 400 Bad Request，没法排查
            String detail = e.getResponseBodyAsString();
            throw new LlmException("LLM 返回 HTTP " + e.getStatusCode().value() + "：" + truncate(detail, 500),
                    e.getStatusCode().value(), e);
        }
        catch (Exception e)
        {
            throw new LlmException("调用 LLM 失败：" + e.getMessage(), null, e);
        }

        long duration = System.currentTimeMillis() - start;
        return parse(raw, cfg, duration);
    }

    private LlmResult parse(String raw, WechatProperties.Llm cfg, long duration)
    {
        try
        {
            JSONObject json = JSON.parseObject(raw);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                throw new LlmException("LLM 响应中没有 choices 字段：" + truncate(raw, 500));
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            String content = message == null ? null : message.getString("content");
            if (content == null)
            {
                throw new LlmException("LLM 响应中没有正文内容：" + truncate(raw, 500));
            }

            JSONObject usage = json.getJSONObject("usage");
            int promptTokens = usage == null ? 0 : usage.getIntValue("prompt_tokens");
            int completionTokens = usage == null ? 0 : usage.getIntValue("completion_tokens");
            String model = json.getString("model");

            log.debug("LLM 调用完成：model={}, tokens={}+{}, 耗时={}ms",
                    model, promptTokens, completionTokens, duration);
            return new LlmResult(content, model == null ? cfg.getModel() : model,
                    promptTokens, completionTokens, duration);
        }
        catch (LlmException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new LlmException("无法解析 LLM 响应：" + truncate(raw, 500), null, e);
        }
    }

    private JSONObject buildBody(List<LlmMessage> messages, WechatProperties.Llm cfg)
    {
        JSONObject body = new JSONObject();
        body.put("model", cfg.getModel());
        List<JSONObject> msgs = new ArrayList<>();
        for (LlmMessage m : messages)
        {
            JSONObject o = new JSONObject();
            o.put("role", m.role());
            o.put("content", m.content());
            msgs.add(o);
        }
        body.put("messages", msgs);
        body.put("temperature", cfg.getTemperature());
        body.put("max_tokens", cfg.getMaxTokens());
        return body;
    }

    /**
     * 懒构造并缓存 RestClient。base-url 改变时需要重启应用（配置项，不热更）。
     */
    private org.springframework.web.client.RestClient getRestClient(WechatProperties.Llm cfg)
    {
        org.springframework.web.client.RestClient client = this.restClient;
        if (client != null)
        {
            return client;
        }
        synchronized (this)
        {
            if (this.restClient == null)
            {
                org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                        new org.springframework.http.client.SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()));
                factory.setReadTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));

                this.restClient = org.springframework.web.client.RestClient.builder()
                        .baseUrl(stripTrailingSlash(cfg.getBaseUrl()))
                        .requestFactory(factory)
                        .build();
            }
            return this.restClient;
        }
    }

    private String stripTrailingSlash(String url)
    {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/"))
        {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private void sleep(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new LlmException("重试等待被中断", null, e);
        }
    }

    private String truncate(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
