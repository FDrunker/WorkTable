package com.ruoyi.knowledge.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.AiChatAssistantData;
import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatConversationDetail;
import com.ruoyi.knowledge.domain.AiChatConversationView;
import com.ruoyi.knowledge.domain.AiChatReplyView;
import com.ruoyi.knowledge.domain.CustomerServiceConfigView;
import com.ruoyi.knowledge.domain.PublicChatSceneView;
import com.ruoyi.knowledge.domain.ResumeAiReplyView;
import com.ruoyi.knowledge.domain.ResumeAiSendRequest;
import com.ruoyi.knowledge.model.PublicChatScene;

/**
 * 个人简历 AI 流程：接收问题，检索简历知识库，生成标准回答并直接返回。
 */
@Service
public class ResumeAiChatService
{
    private static final PublicChatScene SCENE = PublicChatScene.PERSONAL_INFO;

    private final PublicConversationStore conversationStore;

    private final KnowledgeQaService knowledgeQaService;

    public ResumeAiChatService(PublicConversationStore conversationStore, KnowledgeQaService knowledgeQaService)
    {
        this.conversationStore = conversationStore;
        this.knowledgeQaService = knowledgeQaService;
    }

    public ResumeAiBootstrapView bootstrap(String visitorId)
    {
        String visitor = conversationStore.normalizeVisitorId(visitorId, false);
        CustomerServiceConfigView config = knowledgeQaService.getConfig(SCENE.getCode());
        boolean configured = config.chatModelId() != null
                && config.embeddingModelId() != null
                && !config.collectionIds().isEmpty();
        List<AiChatConversationView> conversations = visitor == null
                ? List.of() : conversationStore.listConversations(SCENE.getCode(), visitor);
        return new ResumeAiBootstrapView(
                new PublicChatSceneView(SCENE.getCode(), config.title(), config.welcomeMessage()),
                configured,
                "1".equals(config.enabled()),
                conversations);
    }

    public AiChatConversationView createConversation(String visitorId)
    {
        return conversationStore.createConversation(SCENE.getCode(), visitorId);
    }

    public List<AiChatConversationView> listConversations(String visitorId)
    {
        return conversationStore.listConversations(SCENE.getCode(), visitorId);
    }

    public AiChatConversationDetail getConversation(String conversationId, String visitorId)
    {
        return conversationStore.getConversation(SCENE.getCode(), conversationId, visitorId);
    }

    public void deleteConversation(String conversationId, String visitorId)
    {
        conversationStore.deleteConversation(SCENE.getCode(), conversationId, visitorId);
    }

    public ResumeAiReplyView sendMessage(String conversationId, ResumeAiSendRequest request)
    {
        if (request == null)
        {
            throw new ServiceException("消息内容不能为空");
        }
        String visitor = conversationStore.normalizeVisitorId(request.visitorId(), true);
        String question = knowledgeQaService.normalizeQuestion(request.question());
        AiChatConversation conversation = conversationStore.requireConversation(
                conversationId, visitor, SCENE.getCode());
        KnowledgeQaService.KnowledgeQaAnswer answer = knowledgeQaService.answer(
                SCENE.getCode(),
                question,
                conversationStore.recentHistory(conversationId),
                "");
        AiChatReplyView saved = conversationStore.saveExchange(
                conversation,
                question,
                AiChatAssistantData.standard(answer.answer(), answer.needsClarification(), answer.sources()));
        return new ResumeAiReplyView(
                saved.conversationId(),
                saved.title(),
                saved.userMessage(),
                saved.assistantMessage());
    }

    public record ResumeAiBootstrapView(
            PublicChatSceneView scene,
            boolean configured,
            boolean enabled,
            List<AiChatConversationView> conversations)
    {
    }
}
