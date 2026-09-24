package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.domain.ChromaSearchResult;
import com.ruoyi.knowledge.domain.CollectionView;
import com.ruoyi.knowledge.domain.CustomerServiceChatRequest;
import com.ruoyi.knowledge.domain.CustomerServiceChatResponse;
import com.ruoyi.knowledge.domain.CustomerServiceChatTurn;
import com.ruoyi.knowledge.domain.CustomerServiceConfig;
import com.ruoyi.knowledge.domain.CustomerServiceConfigRequest;
import com.ruoyi.knowledge.domain.CustomerServiceConfigView;
import com.ruoyi.knowledge.domain.CustomerServiceOptions;
import com.ruoyi.knowledge.domain.CustomerServiceSource;
import com.ruoyi.knowledge.mapper.CustomerServiceConfigMapper;
import com.ruoyi.knowledge.model.ModelType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI对话设置与知识库问答编排。
 */
@Service
public class CustomerServiceService
{
    private static final Logger log = LoggerFactory.getLogger(CustomerServiceService.class);

    private static final int DEFAULT_TOP_K = 5;

    private static final int MAX_TOP_K = 20;

    private static final int MAX_QUESTION_LENGTH = 4000;

    private static final int MAX_HISTORY_TURNS = 10;

    private static final int MAX_CONTEXT_LENGTH = 24000;

    private final CustomerServiceConfigMapper configMapper;

    private final AiModelService modelService;

    private final AiModelClientFactory modelClientFactory;

    private final ChromaCollectionService chromaService;

    private final JsonMapper objectMapper;

    public CustomerServiceService(CustomerServiceConfigMapper configMapper, AiModelService modelService,
            AiModelClientFactory modelClientFactory, ChromaCollectionService chromaService, JsonMapper objectMapper)
    {
        this.configMapper = configMapper;
        this.modelService = modelService;
        this.modelClientFactory = modelClientFactory;
        this.chromaService = chromaService;
        this.objectMapper = objectMapper;
    }

    public CustomerServiceConfigView getConfig()
    {
        return toView(configMapper.selectConfig());
    }

    public CustomerServiceOptions options()
    {
        return new CustomerServiceOptions(
                modelService.options(ModelType.CHAT.getCode()),
                modelService.options(ModelType.EMBEDDING.getCode()),
                chromaService.listCollections(null));
    }

    @Transactional
    public CustomerServiceConfigView saveConfig(CustomerServiceConfigRequest request, String username)
    {
        if (request == null)
        {
            throw new ServiceException("AI对话设置不能为空");
        }
        List<String> collectionIds = normalizeCollectionIds(request.collectionIds());
        int topK = normalizeTopK(request.topK());
        validateModel(request.chatModelId(), ModelType.CHAT, "对话模型");
        validateModel(request.embeddingModelId(), ModelType.EMBEDDING, "向量模型");
        validateCollections(collectionIds);

        CustomerServiceConfig config = new CustomerServiceConfig();
        config.setChatModelId(request.chatModelId());
        config.setEmbeddingModelId(request.embeddingModelId());
        config.setCollectionIdsJson(writeCollectionIds(collectionIds));
        config.setTopK(topK);
        config.setEnabled("0".equals(request.enabled()) ? "0" : "1");
        config.setCreateBy(username);
        config.setUpdateBy(username);

        CustomerServiceConfig existing = configMapper.selectConfig();
        if (existing == null)
        {
            configMapper.insertConfig(config);
        }
        else
        {
            config.setConfigId(existing.getConfigId());
            configMapper.updateConfig(config);
        }
        return getConfig();
    }

    public CustomerServiceChatResponse chat(CustomerServiceChatRequest request)
    {
        String question = normalizeQuestion(request);
        CustomerServiceConfig config = requireRuntimeConfig();
        List<String> collectionIds = readCollectionIds(config.getCollectionIdsJson());
        AiModelConfig chatConfig = requireModel(config.getChatModelId(), ModelType.CHAT, "对话模型");
        AiModelConfig embeddingConfig = requireModel(config.getEmbeddingModelId(), ModelType.EMBEDDING, "向量模型");
        ChatModel chatModel = modelClientFactory.createChatModel(chatConfig);

        List<CustomerServiceChatTurn> history = normalizeHistory(request.history());
        IntentResult intent = recognizeIntent(chatModel, question, history);

        EmbeddingModel embeddingModel = modelClientFactory.createEmbeddingModel(embeddingConfig);
        float[] queryEmbedding = embeddingModel.embed(buildRetrievalQuery(question, intent.intent()));
        List<ChromaSearchResult> matches = retrieve(collectionIds, queryEmbedding, normalizeTopK(config.getTopK()));
        List<CustomerServiceSource> sources = toSources(matches);
        if (matches.isEmpty())
        {
            return new CustomerServiceChatResponse(
                    "我暂时无法从知识库确认这个问题。请补充具体场景、产品名称或你想了解的流程，我再为你查询。",
                    intent.intent(), intent.confidence(), true, sources);
        }

        AnswerResult answer = generateAnswer(chatModel, question, history, intent, matches);
        String answerText = StringUtils.trim(answer.answer());
        boolean needsClarification = answer.needsClarification();
        if (StringUtils.isBlank(answerText))
        {
            answerText = StringUtils.isBlank(answer.clarifyingQuestion())
                    ? "这个问题可能需要更多信息。请问你具体想了解哪一项？"
                    : answer.clarifyingQuestion().trim();
            needsClarification = true;
        }
        else if (needsClarification && StringUtils.isNotBlank(answer.clarifyingQuestion()))
        {
            answerText = answer.clarifyingQuestion().trim();
        }
        if (!needsClarification)
        {
            sources = selectSources(sources, answer.usedSourceIds());
        }
        return new CustomerServiceChatResponse(answerText, intent.intent(), intent.confidence(),
                needsClarification, sources);
    }

    private CustomerServiceConfig requireRuntimeConfig()
    {
        CustomerServiceConfig config = configMapper.selectConfig();
        if (config == null || config.getChatModelId() == null || config.getEmbeddingModelId() == null
                || StringUtils.isBlank(config.getCollectionIdsJson()))
        {
            throw new ServiceException("AI对话尚未完成设置");
        }
        if (!"1".equals(config.getEnabled()))
        {
            throw new ServiceException("AI对话已停用");
        }
        if (readCollectionIds(config.getCollectionIdsJson()).isEmpty())
        {
            throw new ServiceException("AI对话尚未选择知识库");
        }
        return config;
    }

    private String normalizeQuestion(CustomerServiceChatRequest request)
    {
        if (request == null || StringUtils.isBlank(request.question()))
        {
            throw new ServiceException("问题内容不能为空");
        }
        String question = request.question().trim();
        if (question.length() > MAX_QUESTION_LENGTH)
        {
            throw new ServiceException("问题内容不能超过 " + MAX_QUESTION_LENGTH + " 个字符");
        }
        return question;
    }

    private IntentResult recognizeIntent(ChatModel chatModel, String question, List<CustomerServiceChatTurn> history)
    {
        String prompt = """
                你是AI对话的意图识别器。请根据对话判断用户当前最可能的咨询意图。
                只输出 JSON，不要输出 Markdown 或额外说明，格式如下：
                {"intent":"简洁的中文意图名称","confidence":0.0}
                confidence 为 0 到 1 之间的小数。

                对话历史：
                %s

                用户当前问题：
                %s
                """.formatted(renderHistory(history), question);
        String content = callModel(chatModel, prompt, "意图识别失败");
        try
        {
            IntentResult result = objectMapper.readValue(stripCodeFence(content), IntentResult.class);
            String intent = StringUtils.isBlank(result.intent()) ? "其他咨询" : result.intent().trim();
            double confidence = result.confidence() == null ? 0D : clamp(result.confidence(), 0D, 1D);
            return new IntentResult(intent, confidence);
        }
        catch (Exception e)
        {
            log.warn("AI对话意图识别结果解析失败：{}", content, e);
            return new IntentResult("其他咨询", 0D);
        }
    }

    private AnswerResult generateAnswer(ChatModel chatModel, String question, List<CustomerServiceChatTurn> history,
            IntentResult intent, List<ChromaSearchResult> matches)
    {
        String prompt = """
                你是严谨的AI对话助手。只能依据下方知识库片段回答，不得编造知识库中没有的事实。
                如果知识片段不足、相互冲突，或问题存在多种理解，needsClarification 必须为 true，
                answer 只提出一个简短、明确的澄清问题，不要猜测答案。
                如果信息充分，needsClarification 为 false，answer 使用简洁中文直接回答用户。
                usedSourceIds 只能填写实际使用的知识片段 id。

                只输出 JSON，不要输出 Markdown 或额外说明，格式如下：
                {"answer":"回答或澄清问题","needsClarification":false,"clarifyingQuestion":"","usedSourceIds":["片段id"]}

                识别出的意图：%s

                对话历史：
                %s

                用户问题：
                %s

                知识库片段：
                %s
                """.formatted(intent.intent(), renderHistory(history), question, renderContexts(matches));
        String content = callModel(chatModel, prompt, "回答生成失败");
        try
        {
            AnswerResult answer = objectMapper.readValue(stripCodeFence(content), AnswerResult.class);
            return answer == null ? new AnswerResult(content, false, "", List.of()) : answer;
        }
        catch (Exception e)
        {
            log.warn("AI对话回答结果解析失败，使用原始文本：{}", content, e);
            return new AnswerResult(content, false, "", List.of());
        }
    }

    private String callModel(ChatModel chatModel, String prompt, String errorMessage)
    {
        try
        {
            String content = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            if (StringUtils.isBlank(content))
            {
                throw new ServiceException(errorMessage + "：模型未返回内容");
            }
            return content.trim();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            String message = StringUtils.isBlank(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
            throw new ServiceException(errorMessage + "：" + trim(message, 800));
        }
    }

    private List<ChromaSearchResult> retrieve(List<String> collectionIds, float[] queryEmbedding, int topK)
    {
        List<ChromaSearchResult> allMatches = new ArrayList<>();
        Exception firstError = null;
        int failedCollections = 0;
        for (String collectionId : collectionIds)
        {
            try
            {
                allMatches.addAll(chromaService.query(collectionId, queryEmbedding, topK));
            }
            catch (Exception e)
            {
                failedCollections++;
                if (firstError == null)
                {
                    firstError = e;
                }
                log.warn("AI对话检索知识库 {} 失败", collectionId, e);
            }
        }
        if (allMatches.isEmpty() && firstError != null && failedCollections == collectionIds.size())
        {
            throw new ServiceException("知识库检索失败：" + trim(firstError.getMessage(), 800));
        }
        return allMatches.stream()
                .sorted(Comparator.comparing(ChromaSearchResult::distance,
                        Comparator.nullsLast(Double::compareTo)))
                .limit(topK)
                .toList();
    }

    private List<CustomerServiceSource> toSources(List<ChromaSearchResult> matches)
    {
        Map<String, String> collectionNames = new LinkedHashMap<>();
        try
        {
            for (CollectionView collection : chromaService.listCollections(null))
            {
                collectionNames.put(collection.id(), collection.name());
            }
        }
        catch (Exception e)
        {
            log.warn("读取AI对话知识库名称失败", e);
        }
        List<CustomerServiceSource> sources = new ArrayList<>(matches.size());
        for (ChromaSearchResult match : matches)
        {
            String collectionId = match.collectionId();
            sources.add(new CustomerServiceSource(
                    match.id(),
                    collectionId,
                    collectionNames.getOrDefault(collectionId, ""),
                    stringMetadata(match.metadata(), "documentId"),
                    stringMetadata(match.metadata(), "documentName"),
                    integerMetadata(match.metadata(), "chunkIndex"),
                    match.content(),
                    match.distance()));
        }
        return sources;
    }

    private List<CustomerServiceSource> selectSources(List<CustomerServiceSource> sources, List<String> usedSourceIds)
    {
        if (usedSourceIds == null || usedSourceIds.isEmpty())
        {
            return sources;
        }
        Set<String> selectedIds = new LinkedHashSet<>(usedSourceIds);
        List<CustomerServiceSource> selected = sources.stream()
                .filter(source -> selectedIds.contains(source.id()))
                .toList();
        return selected.isEmpty() ? sources : selected;
    }

    private String renderHistory(List<CustomerServiceChatTurn> history)
    {
        if (history.isEmpty())
        {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (CustomerServiceChatTurn turn : history)
        {
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "客服" : "用户";
            builder.append(role).append("：").append(StringUtils.trim(turn.content())).append('\n');
        }
        return builder.toString();
    }

    private String renderContexts(List<ChromaSearchResult> matches)
    {
        StringBuilder builder = new StringBuilder();
        int remaining = MAX_CONTEXT_LENGTH;
        for (ChromaSearchResult match : matches)
        {
            String block = """
                    [来源]
                    id: %s
                    document: %s
                    chunkIndex: %s
                    content:
                    %s

                    """.formatted(
                    match.id(),
                    stringMetadata(match.metadata(), "documentName"),
                    integerMetadata(match.metadata(), "chunkIndex"),
                    match.content());
            if (block.length() > remaining)
            {
                block = block.substring(0, Math.max(0, remaining));
            }
            builder.append(block);
            remaining -= block.length();
            if (remaining <= 0)
            {
                break;
            }
        }
        return builder.toString();
    }

    private String buildRetrievalQuery(String question, String intent)
    {
        return StringUtils.isBlank(intent) ? question : "用户意图：" + intent + "\n用户问题：" + question;
    }

    private List<CustomerServiceChatTurn> normalizeHistory(List<CustomerServiceChatTurn> history)
    {
        if (history == null || history.isEmpty())
        {
            return List.of();
        }
        List<CustomerServiceChatTurn> normalized = history.stream()
                .filter(item -> item != null && StringUtils.isNotBlank(item.content()))
                .filter(item -> "user".equalsIgnoreCase(item.role()) || "assistant".equalsIgnoreCase(item.role()))
                .map(item -> new CustomerServiceChatTurn(item.role().toLowerCase(), trim(item.content(), 2000)))
                .toList();
        int fromIndex = Math.max(0, normalized.size() - MAX_HISTORY_TURNS);
        return normalized.subList(fromIndex, normalized.size());
    }

    private CustomerServiceConfigView toView(CustomerServiceConfig config)
    {
        if (config == null)
        {
            return new CustomerServiceConfigView(null, null, null, List.of(),
                    DEFAULT_TOP_K, "1", null);
        }
        return new CustomerServiceConfigView(
                config.getConfigId(),
                config.getChatModelId(),
                config.getEmbeddingModelId(),
                readCollectionIds(config.getCollectionIdsJson()),
                normalizeTopK(config.getTopK()),
                StringUtils.isBlank(config.getEnabled()) ? "1" : config.getEnabled(),
                config.getUpdateTime());
    }

    private void validateModel(Long modelId, ModelType expectedType, String label)
    {
        if (modelId == null)
        {
            throw new ServiceException(label + "不能为空");
        }
        AiModelConfig model = modelService.getRuntimeModel(modelId);
        if (!expectedType.getCode().equals(model.getModelType()))
        {
            throw new ServiceException(label + "类型不正确");
        }
    }

    private AiModelConfig requireModel(Long modelId, ModelType expectedType, String label)
    {
        AiModelConfig model = modelService.getRuntimeModel(modelId);
        if (model == null || !expectedType.getCode().equals(model.getModelType()))
        {
            throw new ServiceException(label + "配置不正确");
        }
        return model;
    }

    private void validateCollections(List<String> collectionIds)
    {
        Set<String> available = new LinkedHashSet<>();
        for (CollectionView collection : chromaService.listCollections(null))
        {
            available.add(collection.id());
        }
        for (String collectionId : collectionIds)
        {
            if (!available.contains(collectionId))
            {
                throw new ServiceException("知识库不存在：" + collectionId);
            }
        }
    }

    private List<String> normalizeCollectionIds(List<String> collectionIds)
    {
        if (collectionIds == null || collectionIds.isEmpty())
        {
            throw new ServiceException("至少选择一个知识库");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String collectionId : collectionIds)
        {
            if (StringUtils.isNotBlank(collectionId))
            {
                normalized.add(collectionId.trim());
            }
        }
        if (normalized.isEmpty())
        {
            throw new ServiceException("至少选择一个知识库");
        }
        return new ArrayList<>(normalized);
    }

    private int normalizeTopK(Integer topK)
    {
        int value = topK == null ? DEFAULT_TOP_K : topK;
        if (value < 1 || value > MAX_TOP_K)
        {
            throw new ServiceException("TopK 必须在 1 到 " + MAX_TOP_K + " 之间");
        }
        return value;
    }

    private String writeCollectionIds(List<String> collectionIds)
    {
        try
        {
            return objectMapper.writeValueAsString(collectionIds);
        }
        catch (JacksonException e)
        {
            throw new ServiceException("知识库配置保存失败");
        }
    }

    private List<String> readCollectionIds(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return List.of();
        }
        try
        {
            String[] values = objectMapper.readValue(json, String[].class);
            return List.of(values);
        }
        catch (Exception e)
        {
            log.warn("解析AI对话知识库配置失败：{}", json, e);
            return List.of();
        }
    }

    private String stripCodeFence(String content)
    {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```"))
        {
            int firstLineEnd = value.indexOf('\n');
            if (firstLineEnd >= 0)
            {
                value = value.substring(firstLineEnd + 1);
            }
            if (value.endsWith("```"))
            {
                value = value.substring(0, value.length() - 3);
            }
        }
        return value.trim();
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

    private double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private String trim(String value, int maxLength)
    {
        String result = value == null ? "" : value;
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    private record IntentResult(String intent, Double confidence)
    {
    }

    private record AnswerResult(
            String answer,
            boolean needsClarification,
            String clarifyingQuestion,
            List<String> usedSourceIds)
    {
    }
}
