package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * 公开 AI 对话页面初始化数据。
 */
public record PublicAiChatBootstrapView(
        PublicChatSceneView scene,
        boolean configured,
        boolean enabled,
        List<AiChatConversationView> conversations)
{
}
