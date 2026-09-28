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

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.domain.ChromaSearchResult;
import com.ruoyi.knowledge.domain.CollectionView;
import com.ruoyi.knowledge.domain.CustomerServiceChatTurn;
import com.ruoyi.knowledge.domain.CustomerServiceConfig;
import com.ruoyi.knowledge.domain.CustomerServiceConfigView;
import com.ruoyi.knowledge.domain.CustomerServiceSource;
import com.ruoyi.knowledge.mapper.CustomerServiceConfigMapper;
import com.ruoyi.knowledge.model.ModelType;
import com.ruoyi.knowledge.model.PublicChatScene;

import tools.jackson.databind.json.JsonMapper;

/**
 * 知识库检索与标准回答引擎，不包含具体业务的分类或工单分支。
 */
@Service
public class KnowledgeQaService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeQaService.class);

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

    public KnowledgeQaService(CustomerServiceConfigMapper configMapper, AiModelService modelService,
            AiModelClientFactory modelClientFactory, ChromaCollectionService chromaService, JsonMapper objectMapper)
    {
        this.configMapper = configMapper;
        this.modelService = modelService;
        this.modelClientFactory = modelClientFactory;
        this.chromaService = chromaService;
        this.objectMapper = objectMapper;
    }

    public CustomerServiceConfigView getConfig(String scene)
    {
        PublicChatScene chatScene = PublicChatScene.normalize(scene);
        return toView(configMapper.selectConfig(chatScene.getCode()), chatScene);
    }

    public KnowledgeQaAnswer answer(String scene, String question, List<CustomerServiceChatTurn> history,
            String retrievalContext)
    {
        String normalizedQuestion = normalizeQuestion(question);
        CustomerServiceConfig config = requireRuntimeConfig(PublicChatScene.normalize(scene).getCode());
        List<String> collectionIds = readCollectionIds(config.getCollectionIdsJson());
        AiModelConfig chatConfig = requireModel(config.getChatModelId(), ModelType.CHAT, "对话模型");
        AiModelConfig embeddingConfig = requireModel(config.getEmbeddingModelId(), ModelType.EMBEDDING, "向量模型");
        ChatModel chatModel = modelClientFactory.createChatModel(chatConfig);
        List<CustomerServiceChatTurn> normalizedHistory = normalizeHistory(history);

        EmbeddingModel embeddingModel = modelClientFactory.createEmbeddingModel(embeddingConfig);
        float[] queryEmbedding = embeddingModel.embed(buildRetrievalQuery(normalizedQuestion, retrievalContext));
        List<ChromaSearchResult> matches = retrieve(collectionIds, queryEmbedding, normalizeTopK(config.getTopK()));
        List<CustomerServiceSource> sources = toSources(matches);
        if (matches.isEmpty())
        {
            return new KnowledgeQaAnswer(
                    "我暂时无法从知识库确认这个问题。请补充具体信息后再试。",
                    true,
                    sources);
        }

        AnswerResult answer = generateAnswer(chatModel, normalizedQuestion, normalizedHistory,
                retrievalContext, matches);
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
        return new KnowledgeQaAnswer(answerText, needsClarification, sources);
    }

    public String normalizeQuestion(String question)
    {
        if (StringUtils.isBlank(question))
        {
            throw new ServiceException("问题内容不能为空");
        }
        String value = question.trim();
        if (value.length() > MAX_QUESTION_LENGTH)
        {
            throw new ServiceException("问题内容不能超过 " + MAX_QUESTION_LENGTH + " 个字符");
        }
        return value;
    }

    private CustomerServiceConfig requireRuntimeConfig(String scene)
    {
        CustomerServiceConfig config = configMapper.selectConfig(scene);
        if (config == null || config.getChatModelId() == null || config.getEmbeddingModelId() == null
                || StringUtils.isBlank(config.getCollectionIdsJson()))
        {
            throw new ServiceException("AI 对话尚未完成设置");
        }
        if (!"1".equals(config.getEnabled()))
        {
            throw new ServiceException("AI 对话已停用");
        }
        if (readCollectionIds(config.getCollectionIdsJson()).isEmpty())
        {
            throw new ServiceException("AI 对话尚未选择知识库");
        }
        return config;
    }

    private AnswerResult generateAnswer(ChatModel chatModel, String question,
            List<CustomerServiceChatTurn> history, String retrievalContext, List<ChromaSearchResult> matches)
    {
        String prompt = """
                你是严谨的知识库问答助手。只能依据下方知识库片段回答，不得编造知识库中没有的事实。
                如果知识片段不足、相互冲突，或问题存在多种理解，needsClarification 必须为 true，
                answer 只提出一个简短、明确的澄清问题，不要猜测答案。
                如果信息充分，needsClarification 为 false，answer 使用简洁中文直接回答用户。
                usedSourceIds 只能填写实际使用的知识片段 id。

                只输出 JSON，不要输出 Markdown 或额外说明，格式如下：
                {"answer":"回答或澄清问题","needsClarification":false,"clarifyingQuestion":"","usedSourceIds":["片段id"]}

                业务上下文：%s

                对话历史：
                %s

                用户问题：
                %s

                知识库片段：
                %s
                """.formatted(StringUtils.isBlank(retrievalContext) ? "无" : retrievalContext,
                renderHistory(history), question, renderContexts(matches));
        String content = callModel(chatModel, prompt, "回答生成失败");
        try
        {
            AnswerResult answer = objectMapper.readValue(stripCodeFence(content), AnswerResult.class);
            return answer == null ? new AnswerResult(content, false, "", List.of()) : answer;
        }
        catch (Exception e)
        {
            log.warn("知识库回答结果解析失败，使用原始文本：{}", content, e);
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
                log.warn("知识库检索 {} 失败", collectionId, e);
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
            log.warn("读取知识库名称失败", e);
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
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "助手" : "用户";
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

    private String buildRetrievalQuery(String question, String retrievalContext)
    {
        return StringUtils.isBlank(retrievalContext) ? question : retrievalContext + "\n用户问题：" + question;
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

    private CustomerServiceConfigView toView(CustomerServiceConfig config, PublicChatScene scene)
    {
        if (config == null)
        {
            return new CustomerServiceConfigView(null, scene.getCode(), scene.getDefaultTitle(),
                    scene.getDefaultWelcomeMessage(), null, null, List.of(),
                    DEFAULT_TOP_K, "1", null);
        }
        return new CustomerServiceConfigView(
                config.getConfigId(),
                scene.getCode(),
                StringUtils.isBlank(config.getTitle()) ? scene.getDefaultTitle() : config.getTitle(),
                StringUtils.isBlank(config.getWelcomeMessage())
                        ? scene.getDefaultWelcomeMessage() : config.getWelcomeMessage(),
                config.getChatModelId(),
                config.getEmbeddingModelId(),
                readCollectionIds(config.getCollectionIdsJson()),
                normalizeTopK(config.getTopK()),
                StringUtils.isBlank(config.getEnabled()) ? "1" : config.getEnabled(),
                config.getUpdateTime());
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

    private int normalizeTopK(Integer topK)
    {
        int value = topK == null ? DEFAULT_TOP_K : topK;
        if (value < 1 || value > MAX_TOP_K)
        {
            throw new ServiceException("TopK 必须在 1 到 " + MAX_TOP_K + " 之间");
        }
        return value;
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
            log.warn("解析知识库配置失败：{}", json, e);
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

    private String trim(String value, int maxLength)
    {
        String result = value == null ? "" : value;
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    public record KnowledgeQaAnswer(
            String answer,
            boolean needsClarification,
            List<CustomerServiceSource> sources)
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
