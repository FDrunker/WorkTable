package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.AiChatAssistantData;
import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatConversationDetail;
import com.ruoyi.knowledge.domain.AiChatConversationView;
import com.ruoyi.knowledge.domain.AiChatMessage;
import com.ruoyi.knowledge.domain.AiChatMessageView;
import com.ruoyi.knowledge.domain.AiChatReplyView;
import com.ruoyi.knowledge.domain.CustomerServiceChatTurn;
import com.ruoyi.knowledge.domain.CustomerServiceSource;
import com.ruoyi.knowledge.mapper.AiChatConversationMapper;

import tools.jackson.databind.json.JsonMapper;

/**
 * 公开会话与消息的持久化。两类 AI 的问答流程各自独立，只在这里共享会话存储。
 */
@Service
public class PublicConversationStore
{
    private static final Pattern VISITOR_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private static final int MAX_TITLE_LENGTH = 30;

    private static final int HISTORY_MESSAGE_LIMIT = 10;

    private final AiChatConversationMapper conversationMapper;

    private final JsonMapper objectMapper;

    public PublicConversationStore(AiChatConversationMapper conversationMapper, JsonMapper objectMapper)
    {
        this.conversationMapper = conversationMapper;
        this.objectMapper = objectMapper;
    }

    public AiChatConversationView createConversation(String scene, String visitorId)
    {
        String visitor = normalizeVisitorId(visitorId, true);
        AiChatConversation conversation = new AiChatConversation();
        conversation.setConversationId(newConversationId());
        conversation.setVisitorId(visitor);
        conversation.setScene(scene);
        conversation.setTitle("新对话");
        conversationMapper.insertConversation(conversation);
        return toConversationView(conversationMapper.selectConversationById(conversation.getConversationId()));
    }

    public List<AiChatConversationView> listConversations(String scene, String visitorId)
    {
        String visitor = normalizeVisitorId(visitorId, true);
        return conversationMapper.selectConversationList(visitor, scene).stream()
                .map(this::toConversationView)
                .toList();
    }

    public AiChatConversationDetail getConversation(String scene, String conversationId, String visitorId)
    {
        String visitor = normalizeVisitorId(visitorId, true);
        AiChatConversation conversation = requireConversation(conversationId, visitor, scene);
        List<AiChatMessageView> messages = conversationMapper.selectMessages(conversation.getConversationId()).stream()
                .map(this::toMessageView)
                .toList();
        return new AiChatConversationDetail(
                conversation.getConversationId(),
                conversation.getScene(),
                conversation.getTitle(),
                conversation.getCreateTime(),
                conversation.getUpdateTime(),
                messages);
    }

    @Transactional
    public void deleteConversation(String scene, String conversationId, String visitorId)
    {
        String visitor = normalizeVisitorId(visitorId, true);
        AiChatConversation conversation = requireConversation(conversationId, visitor, scene);
        conversationMapper.deleteMessages(conversation.getConversationId());
        conversationMapper.deleteConversation(conversation.getConversationId());
    }

    public AiChatConversation requireConversation(String conversationId, String visitorId, String scene)
    {
        if (StringUtils.isBlank(conversationId))
        {
            throw new ServiceException("对话ID不能为空");
        }
        AiChatConversation conversation = conversationMapper.selectConversationById(conversationId.trim());
        if (conversation == null
                || !visitorId.equals(conversation.getVisitorId())
                || !scene.equals(conversation.getScene()))
        {
            throw new ServiceException("对话不存在或无权访问");
        }
        return conversation;
    }

    public List<CustomerServiceChatTurn> recentHistory(String conversationId)
    {
        List<AiChatMessage> messages = new ArrayList<>(
                conversationMapper.selectRecentMessages(conversationId, HISTORY_MESSAGE_LIMIT));
        Collections.reverse(messages);
        return messages.stream()
                .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                .map(message -> new CustomerServiceChatTurn(message.getRole(), message.getContent()))
                .toList();
    }

    @Transactional
    public AiChatReplyView saveExchange(AiChatConversation conversation, String question,
            AiChatAssistantData assistantData)
    {
        boolean firstMessage = conversationMapper.countMessages(conversation.getConversationId()) == 0;

        AiChatMessage userMessage = new AiChatMessage();
        userMessage.setConversationId(conversation.getConversationId());
        userMessage.setRole("user");
        userMessage.setContent(question);
        userMessage.setNeedsClarification("0");
        userMessage.setCreateTime(new Date());
        conversationMapper.insertMessage(userMessage);

        AiChatMessage assistantMessage = new AiChatMessage();
        assistantMessage.setConversationId(conversation.getConversationId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(assistantData.content());
        assistantMessage.setIntent(assistantData.intent());
        assistantMessage.setIntentConfidence(assistantData.intentConfidence());
        assistantMessage.setNeedsClarification(assistantData.needsClarification() ? "1" : "0");
        assistantMessage.setSourcesJson(writeSources(assistantData.sources()));
        assistantMessage.setCategory(assistantData.category());
        assistantMessage.setPriority(assistantData.priority());
        assistantMessage.setTicketNo(assistantData.ticketNo());
        assistantMessage.setCreateTime(new Date());
        conversationMapper.insertMessage(assistantMessage);

        String title = conversation.getTitle();
        if (firstMessage)
        {
            title = buildTitle(question);
            conversationMapper.updateConversationTitle(conversation.getConversationId(), title);
        }
        else
        {
            conversationMapper.touchConversation(conversation.getConversationId());
        }
        return new AiChatReplyView(
                conversation.getConversationId(),
                title,
                toMessageView(userMessage),
                toMessageView(assistantMessage));
    }

    public String normalizeVisitorId(String visitorId, boolean required)
    {
        if (StringUtils.isBlank(visitorId))
        {
            if (required)
            {
                throw new ServiceException("访客标识不能为空");
            }
            return null;
        }
        String value = visitorId.trim();
        if (!VISITOR_ID_PATTERN.matcher(value).matches())
        {
            throw new ServiceException("访客标识格式不正确");
        }
        return value;
    }

    private AiChatConversationView toConversationView(AiChatConversation conversation)
    {
        if (conversation == null)
        {
            throw new ServiceException("对话创建失败");
        }
        return new AiChatConversationView(
                conversation.getConversationId(),
                conversation.getTitle(),
                conversation.getCreateTime(),
                conversation.getUpdateTime(),
                conversation.getMessageCount() == null ? 0 : conversation.getMessageCount(),
                conversation.getLastMessage());
    }

    private AiChatMessageView toMessageView(AiChatMessage message)
    {
        return new AiChatMessageView(
                message.getMessageId(),
                message.getRole(),
                message.getContent(),
                message.getIntent(),
                message.getIntentConfidence(),
                "1".equals(message.getNeedsClarification()),
                readSources(message.getSourcesJson()),
                message.getCategory(),
                message.getPriority(),
                message.getTicketNo(),
                message.getCreateTime());
    }

    private String buildTitle(String question)
    {
        String title = question.replaceAll("\\s+", " ").trim();
        if (title.length() > MAX_TITLE_LENGTH)
        {
            title = title.substring(0, MAX_TITLE_LENGTH) + "...";
        }
        return StringUtils.isBlank(title) ? "新对话" : title;
    }

    private String newConversationId()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String writeSources(List<CustomerServiceSource> sources)
    {
        if (sources == null || sources.isEmpty())
        {
            return "[]";
        }
        try
        {
            return objectMapper.writeValueAsString(sources);
        }
        catch (Exception e)
        {
            return "[]";
        }
    }

    private List<CustomerServiceSource> readSources(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return List.of();
        }
        try
        {
            CustomerServiceSource[] sources = objectMapper.readValue(json, CustomerServiceSource[].class);
            return List.of(sources);
        }
        catch (Exception e)
        {
            return List.of();
        }
    }
}
