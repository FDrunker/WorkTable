package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.ruoyi.knowledge.domain.ChromaRecord;
import com.ruoyi.knowledge.domain.CollectionView;
import com.ruoyi.knowledge.domain.CustomerServiceChatTurn;
import com.ruoyi.knowledge.domain.CustomerServiceSource;
import com.ruoyi.knowledge.mapper.AiChatConversationMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 公开会话与消息的持久化。两类 AI 的问答流程各自独立，只在这里共享会话存储。
 */
@Service
public class PublicConversationStore
{
    private static final Logger log = LoggerFactory.getLogger(PublicConversationStore.class);

    private static final Pattern VISITOR_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private static final int MAX_TITLE_LENGTH = 30;

    private static final int HISTORY_MESSAGE_LIMIT = 10;

    private final AiChatConversationMapper conversationMapper;

    private final ChromaCollectionService chromaService;

    private final JsonMapper objectMapper;

    public PublicConversationStore(AiChatConversationMapper conversationMapper,
            ChromaCollectionService chromaService, JsonMapper objectMapper)
    {
        this.conversationMapper = conversationMapper;
        this.chromaService = chromaService;
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
        List<AiChatMessageView> messages = toMessageViews(
                conversationMapper.selectMessages(conversation.getConversationId()));
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
                toMessageView(userMessage, Map.of()),
                toMessageView(assistantMessage, indexSources(assistantData.sources())));
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

    private List<AiChatMessageView> toMessageViews(List<AiChatMessage> messages)
    {
        List<SourceReference> references = messages.stream()
                .flatMap(message -> readSourceReferences(message.getSourcesJson()).stream())
                .distinct()
                .toList();
        Map<SourceReference, CustomerServiceSource> sources = loadSources(references);
        return messages.stream()
                .map(message -> toMessageView(message, sources))
                .toList();
    }

    private AiChatMessageView toMessageView(AiChatMessage message,
            Map<SourceReference, CustomerServiceSource> sources)
    {
        List<CustomerServiceSource> messageSources = readSourceReferences(message.getSourcesJson()).stream()
                .map(sources::get)
                .filter(source -> source != null)
                .toList();
        return new AiChatMessageView(
                message.getMessageId(),
                message.getRole(),
                message.getContent(),
                message.getIntent(),
                message.getIntentConfidence(),
                "1".equals(message.getNeedsClarification()),
                messageSources,
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
            List<SourceReference> references = sources.stream()
                    .map(this::toSourceReference)
                    .filter(reference -> reference != null)
                    .distinct()
                    .toList();
            return objectMapper.writeValueAsString(references);
        }
        catch (Exception e)
        {
            return "[]";
        }
    }

    private List<SourceReference> readSourceReferences(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return List.of();
        }
        try
        {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray())
            {
                return List.of();
            }
            List<SourceReference> references = new ArrayList<>();
            for (JsonNode item : root)
            {
                SourceReference reference = readSourceReference(item);
                if (reference != null)
                {
                    references.add(reference);
                }
            }
            return references;
        }
        catch (Exception e)
        {
            log.warn("解析消息知识来源失败", e);
            return List.of();
        }
    }

    private SourceReference readSourceReference(JsonNode item)
    {
        if (item == null || item.isNull())
        {
            return null;
        }
        String recordId = textValue(item, "id");
        String collectionId = textValue(item, "collectionId");
        return StringUtils.isBlank(recordId) || StringUtils.isBlank(collectionId)
                ? null : new SourceReference(recordId, collectionId);
    }

    private String textValue(JsonNode item, String field)
    {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? "" : value.stringValue("");
    }

    private SourceReference toSourceReference(CustomerServiceSource source)
    {
        if (source == null || StringUtils.isBlank(source.id()) || StringUtils.isBlank(source.collectionId()))
        {
            return null;
        }
        return new SourceReference(source.id(), source.collectionId());
    }

    private Map<SourceReference, CustomerServiceSource> indexSources(List<CustomerServiceSource> sources)
    {
        if (sources == null || sources.isEmpty())
        {
            return Map.of();
        }
        Map<SourceReference, CustomerServiceSource> indexed = new LinkedHashMap<>();
        for (CustomerServiceSource source : sources)
        {
            SourceReference reference = toSourceReference(source);
            if (reference != null)
            {
                indexed.put(reference, source);
            }
        }
        return indexed;
    }

    private Map<SourceReference, CustomerServiceSource> loadSources(List<SourceReference> references)
    {
        if (references.isEmpty())
        {
            return Map.of();
        }
        Map<String, String> collectionNames = loadCollectionNames();
        Map<String, ChromaRecord> records = new LinkedHashMap<>();
        Map<String, Boolean> loadedCollections = new LinkedHashMap<>();
        for (SourceReference reference : references)
        {
            if (loadedCollections.putIfAbsent(reference.collectionId(), Boolean.TRUE) != null)
            {
                continue;
            }
            List<String> recordIds = references.stream()
                    .filter(item -> reference.collectionId().equals(item.collectionId()))
                    .map(SourceReference::id)
                    .distinct()
                    .toList();
            try
            {
                for (ChromaRecord record : chromaService.getRecords(reference.collectionId(), recordIds))
                {
                    records.put(recordKey(reference.collectionId(), record.id()), record);
                }
            }
            catch (Exception e)
            {
                log.warn("读取消息知识来源失败，知识库：{}", reference.collectionId(), e);
            }
        }
        Map<SourceReference, CustomerServiceSource> sources = new LinkedHashMap<>();
        for (SourceReference reference : references)
        {
            ChromaRecord record = records.get(recordKey(reference.collectionId(), reference.id()));
            if (record == null)
            {
                continue;
            }
            sources.put(reference, new CustomerServiceSource(
                    reference.id(),
                    reference.collectionId(),
                    collectionNames.getOrDefault(reference.collectionId(), ""),
                    stringMetadata(record.metadata(), "documentId"),
                    stringMetadata(record.metadata(), "documentName"),
                    integerMetadata(record.metadata(), "chunkIndex"),
                    record.content(),
                    null));
        }
        return sources;
    }

    private Map<String, String> loadCollectionNames()
    {
        try
        {
            Map<String, String> names = new LinkedHashMap<>();
            for (CollectionView collection : chromaService.listCollections(null))
            {
                names.put(collection.id(), collection.name());
            }
            return names;
        }
        catch (Exception e)
        {
            log.warn("读取消息知识库名称失败", e);
            return Map.of();
        }
    }

    private String recordKey(String collectionId, String recordId)
    {
        return collectionId + "\u0000" + recordId;
    }

    private String stringMetadata(Map<String, Object> metadata, String key)
    {
        if (metadata == null || metadata.get(key) == null)
        {
            return "";
        }
        return String.valueOf(metadata.get(key));
    }

    private Integer integerMetadata(Map<String, Object> metadata, String key)
    {
        if (metadata == null || metadata.get(key) == null)
        {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return Integer.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private record SourceReference(String id, String collectionId)
    {
    }
}
