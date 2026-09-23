package com.ruoyi.knowledge.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 支持动态接入的模型提供商。
 */
public enum ModelProvider
{
    OPENAI("openai", "OpenAI / ChatGPT", "https://api.openai.com", Set.of("embedding", "chat")),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", Set.of("chat")),
    OLLAMA("ollama", "Ollama", "http://localhost:11434", Set.of("embedding", "chat")),
    TYPESAFE("typesafe", "Jev / TypeSafe", "https://api.typesafe.ai", Set.of("system-one"));

    private final String code;

    private final String displayName;

    private final String defaultBaseUrl;

    private final Set<String> modelTypes;

    ModelProvider(String code, String displayName, String defaultBaseUrl, Set<String> modelTypes)
    {
        this.code = code;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.modelTypes = new LinkedHashSet<>(modelTypes);
    }

    public String getCode()
    {
        return code;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getDefaultBaseUrl()
    {
        return defaultBaseUrl;
    }

    public Set<String> getModelTypes()
    {
        return modelTypes;
    }

    public boolean supports(String modelType)
    {
        return modelTypes.contains(modelType);
    }

    public static ModelProvider fromCode(String code)
    {
        return Arrays.stream(values())
                .filter(provider -> provider.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的模型提供商：" + code));
    }
}
