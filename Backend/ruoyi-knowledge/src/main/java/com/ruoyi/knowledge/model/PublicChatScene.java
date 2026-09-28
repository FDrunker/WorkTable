package com.ruoyi.knowledge.model;

import java.util.Arrays;
import java.util.List;

import com.ruoyi.common.exception.ServiceException;

/**
 * AI 对话场景。后台和公开页面使用同一套配置与问答能力。
 */
public enum PublicChatScene
{
    ADMIN("admin", "后台 AI 对话", "你好，有什么可以帮你的？", false),
    PERSONAL_INFO("personal-info", "个人简历 AI", "你好，我可以依据简历知识库回答你的问题。", true),
    CUSTOMER_SERVICE("customer-service", "智能客服", "你好，我是智能客服，请告诉我你需要咨询的问题。", true);

    private final String code;

    private final String defaultTitle;

    private final String defaultWelcomeMessage;

    private final boolean publicAccess;

    PublicChatScene(String code, String defaultTitle, String defaultWelcomeMessage, boolean publicAccess)
    {
        this.code = code;
        this.defaultTitle = defaultTitle;
        this.defaultWelcomeMessage = defaultWelcomeMessage;
        this.publicAccess = publicAccess;
    }

    public String getCode()
    {
        return code;
    }

    public String getDefaultTitle()
    {
        return defaultTitle;
    }

    public String getDefaultWelcomeMessage()
    {
        return defaultWelcomeMessage;
    }

    public boolean isPublicAccess()
    {
        return publicAccess;
    }

    public static PublicChatScene normalize(String scene)
    {
        if (scene == null || scene.isBlank())
        {
            return ADMIN;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equals(scene.trim()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("不支持的 AI 对话场景：" + scene));
    }

    public static PublicChatScene requirePublic(String scene)
    {
        PublicChatScene value = normalize(scene);
        if (!value.publicAccess)
        {
            throw new ServiceException("该 AI 对话场景不允许公开访问");
        }
        return value;
    }

    public static List<PublicChatScene> publicScenes()
    {
        return Arrays.stream(values()).filter(PublicChatScene::isPublicAccess).toList();
    }
}
