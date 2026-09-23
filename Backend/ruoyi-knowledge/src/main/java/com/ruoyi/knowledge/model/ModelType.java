package com.ruoyi.knowledge.model;

import java.util.Arrays;

public enum ModelType
{
    EMBEDDING("embedding", "Embedding"),
    CHAT("chat", "Chat"),
    SYSTEM_ONE("system-one", "System One");

    private final String code;

    private final String displayName;

    ModelType(String code, String displayName)
    {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode()
    {
        return code;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public static ModelType fromCode(String code)
    {
        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的模型类型：" + code));
    }
}
