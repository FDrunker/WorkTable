package com.ruoyi.knowledge.service;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.AiChatAssistantData;
import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatConversationDetail;
import com.ruoyi.knowledge.domain.AiChatConversationView;
import com.ruoyi.knowledge.domain.AiChatMessageView;
import com.ruoyi.knowledge.domain.AiChatReplyView;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.domain.CustomerServiceConfigView;
import com.ruoyi.knowledge.domain.CustomerServiceReplyView;
import com.ruoyi.knowledge.domain.CustomerServiceSendRequest;
import com.ruoyi.knowledge.domain.CustomerServiceTicket;
import com.ruoyi.knowledge.domain.PublicChatSceneView;
import com.ruoyi.knowledge.model.ModelType;
import com.ruoyi.knowledge.model.PublicChatScene;

import tools.jackson.databind.json.JsonMapper;

/**
 * 智能客服流程：分类、标准问题检索回答、复杂问题预分析并创建人工工单。
 */
@Service
public class CustomerServiceAiChatService
{
    private static final Logger log = LoggerFactory.getLogger(CustomerServiceAiChatService.class);

    private static final PublicChatScene SCENE = PublicChatScene.CUSTOMER_SERVICE;

    private static final int MAX_ISSUE_TYPE_LENGTH = 64;

    private static final String WAITING_MESSAGE = "已记录您的问题，等待人工给您处理。";

    private final PublicConversationStore conversationStore;

    private final KnowledgeQaService knowledgeQaService;

    private final AiModelService modelService;

    private final AiModelClientFactory modelClientFactory;

    private final CustomerServiceTicketService ticketService;

    private final JsonMapper objectMapper;

    public CustomerServiceAiChatService(PublicConversationStore conversationStore,
            KnowledgeQaService knowledgeQaService, AiModelService modelService,
            AiModelClientFactory modelClientFactory, CustomerServiceTicketService ticketService,
            JsonMapper objectMapper)
    {
        this.conversationStore = conversationStore;
        this.knowledgeQaService = knowledgeQaService;
        this.modelService = modelService;
        this.modelClientFactory = modelClientFactory;
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    public CustomerServiceAiBootstrapView bootstrap(String visitorId)
    {
        String visitor = conversationStore.normalizeVisitorId(visitorId, false);
        CustomerServiceConfigView config = knowledgeQaService.getConfig(SCENE.getCode());
        boolean configured = config.chatModelId() != null
                && config.embeddingModelId() != null
                && !config.collectionIds().isEmpty();
        List<AiChatConversationView> conversations = visitor == null
                ? List.of() : conversationStore.listConversations(SCENE.getCode(), visitor);
        return new CustomerServiceAiBootstrapView(
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

    public CustomerServiceReplyView sendMessage(String conversationId, CustomerServiceSendRequest request)
    {
        if (request == null)
        {
            throw new ServiceException("工单内容不能为空");
        }
        String visitor = conversationStore.normalizeVisitorId(request.visitorId(), true);
        String issueType = normalizeIssueType(request.issueType());
        String question = knowledgeQaService.normalizeQuestion(request.question());
        AiChatConversation conversation = conversationStore.requireConversation(
                conversationId, visitor, SCENE.getCode());
        ChatModel chatModel = runtimeChatModel();
        ClassificationResult classification = classify(chatModel, issueType, question);

        if ("standard".equals(classification.category()))
        {
            KnowledgeQaService.KnowledgeQaAnswer answer = knowledgeQaService.answer(
                    SCENE.getCode(),
                    question,
                    conversationStore.recentHistory(conversationId),
                    "客服问题类型：" + issueType);
            AiChatReplyView saved = conversationStore.saveExchange(
                    conversation,
                    question,
                    new AiChatAssistantData(
                            answer.answer(),
                            classification.category(),
                            classification.confidence(),
                            answer.needsClarification(),
                            answer.sources(),
                            classification.category(),
                            classification.priority(),
                            null));
            return toReply(saved, classification.category(), classification.priority(), null, "RESOLVED");
        }

        PreanalysisResult preanalysis = preanalyze(chatModel, issueType, question, classification);
        CustomerServiceTicket ticket = ticketService.createComplexTicket(
                conversationId,
                visitor,
                issueType,
                question,
                classification.category(),
                classification.reason(),
                classification.priority(),
                preanalysis.summary(),
                preanalysis.solution(),
                preanalysis.suggestedReply());
        AiChatReplyView saved = conversationStore.saveExchange(
                conversation,
                question,
                new AiChatAssistantData(
                        WAITING_MESSAGE,
                        classification.category(),
                        classification.confidence(),
                        false,
                        List.of(),
                        classification.category(),
                        classification.priority(),
                        ticket.getTicketNo()));
        return toReply(saved, classification.category(), classification.priority(),
                ticket.getTicketNo(), ticket.getStatus());
    }

    private ChatModel runtimeChatModel()
    {
        CustomerServiceConfigView config = knowledgeQaService.getConfig(SCENE.getCode());
        if (config.chatModelId() == null || config.embeddingModelId() == null || config.collectionIds().isEmpty())
        {
            throw new ServiceException("AI 对话尚未完成设置");
        }
        if (!"1".equals(config.enabled()))
        {
            throw new ServiceException("AI 对话已停用");
        }
        AiModelConfig model = modelService.getRuntimeModel(config.chatModelId());
        if (model == null || !ModelType.CHAT.getCode().equals(model.getModelType()))
        {
            throw new ServiceException("对话模型配置不正确");
        }
        return modelClientFactory.createChatModel(model);
    }

    private ClassificationResult classify(ChatModel chatModel, String issueType, String question)
    {
        String prompt = """
                你是一个客服工单分类专家。请分析以下工单，输出 JSON 格式的分类结果。
                只输出 JSON，不要输出 Markdown 或额外说明。

                output_schema:
                {
                  "category": "standard 或 complex",
                  "reason": "分类理由（一句话）",
                  "priority": "low / medium / high",
                  "confidence": 0.0
                }

                criteria:
                standard: 物流查询、退货政策、普通退款（<500元）、账号问题
                complex: 大额退款（>500元）、投诉、纠纷、产品质量问题

                用户选择的问题类型：%s
                工单内容：%s
                """.formatted(issueType, question);
        String content = callModel(chatModel, prompt, "工单分类失败");
        try
        {
            ClassificationResult result = objectMapper.readValue(stripCodeFence(content), ClassificationResult.class);
            String category = StringUtils.trim(result.category()).toLowerCase(Locale.ROOT);
            if (!"standard".equals(category) && !"complex".equals(category))
            {
                throw new ServiceException("工单分类结果不正确");
            }
            String priority = normalizePriority(result.priority());
            double confidence = result.confidence() == null ? 0D : clamp(result.confidence(), 0D, 1D);
            return new ClassificationResult(
                    category,
                    trim(StringUtils.isBlank(result.reason()) ? "模型未提供分类理由" : result.reason().trim(), 500),
                    priority,
                    confidence);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("智能客服工单分类结果解析失败：{}", content, e);
            throw new ServiceException("工单分类失败：" + trim(e.getMessage(), 300));
        }
    }

    private PreanalysisResult preanalyze(ChatModel chatModel, String issueType, String question,
            ClassificationResult classification)
    {
        String prompt = """
                你是智能客服复杂工单预分析助手。请生成问题摘要、建议处理方案和可编辑的建议回复草稿。
                只输出 JSON，不要输出 Markdown 或额外说明，格式如下：
                {
                  "summary": "问题摘要",
                  "solution": "建议处理方案",
                  "suggestedReply": "给用户的建议回复草稿"
                }

                问题类型：%s
                优先级：%s
                分类理由：%s
                工单内容：%s
                """.formatted(issueType, classification.priority(), classification.reason(), question);
        String content = callModel(chatModel, prompt, "问题预分析失败");
        try
        {
            PreanalysisResult result = objectMapper.readValue(stripCodeFence(content), PreanalysisResult.class);
            String summary = StringUtils.trim(result.summary());
            String solution = StringUtils.trim(result.solution());
            String suggestedReply = StringUtils.trim(result.suggestedReply());
            if (StringUtils.isBlank(summary) || StringUtils.isBlank(solution) || StringUtils.isBlank(suggestedReply))
            {
                throw new ServiceException("问题预分析结果不完整");
            }
            return new PreanalysisResult(summary, solution, suggestedReply);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("智能客服问题预分析结果解析失败：{}", content, e);
            throw new ServiceException("问题预分析失败：" + trim(e.getMessage(), 300));
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

    private CustomerServiceReplyView toReply(AiChatReplyView saved, String category,
            String priority, String ticketNo, String ticketStatus)
    {
        AiChatMessageView assistant = saved.assistantMessage();
        return new CustomerServiceReplyView(
                saved.conversationId(),
                saved.title(),
                saved.userMessage(),
                assistant,
                category,
                priority,
                ticketNo,
                ticketStatus);
    }

    private String normalizeIssueType(String issueType)
    {
        String value = StringUtils.isBlank(issueType) ? "其他问题" : issueType.trim();
        if (value.length() > MAX_ISSUE_TYPE_LENGTH)
        {
            throw new ServiceException("问题类型不能超过 " + MAX_ISSUE_TYPE_LENGTH + " 个字符");
        }
        return value;
    }

    private String normalizePriority(String priority)
    {
        String value = StringUtils.isBlank(priority) ? "medium" : priority.trim().toLowerCase(Locale.ROOT);
        if (!"low".equals(value) && !"medium".equals(value) && !"high".equals(value))
        {
            throw new ServiceException("工单优先级不正确");
        }
        return value;
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

    private double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private String trim(String value, int maxLength)
    {
        String result = value == null ? "" : value;
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    private record ClassificationResult(
            String category,
            String reason,
            String priority,
            Double confidence)
    {
    }

    private record PreanalysisResult(
            String summary,
            String solution,
            String suggestedReply)
    {
    }

    public record CustomerServiceAiBootstrapView(
            PublicChatSceneView scene,
            boolean configured,
            boolean enabled,
            List<AiChatConversationView> conversations)
    {
    }
}
