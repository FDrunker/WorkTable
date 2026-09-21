package com.ruoyi.wechat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ruoyi.wechat.clean.ChatContextRenderer;
import com.ruoyi.wechat.clean.CleanResult;
import com.ruoyi.wechat.clean.CleanedMessage;
import com.ruoyi.wechat.clean.WechatMessageCleaner;
import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.FetchQuery;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.SourceStatus;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.domain.AnalysisPreview;
import com.ruoyi.wechat.domain.AnalysisRequest;
import com.ruoyi.wechat.domain.AnalysisResponse;
import com.ruoyi.wechat.domain.IdentityCandidate;
import com.ruoyi.wechat.identity.ChatIdentity;
import com.ruoyi.wechat.llm.LlmClient;
import com.ruoyi.wechat.llm.LlmMessage;
import com.ruoyi.wechat.llm.LlmResult;
import com.ruoyi.wechat.skill.AnalysisResult;
import com.ruoyi.wechat.skill.AnalysisResultParser;
import com.ruoyi.wechat.skill.SafetyGuard;
import com.ruoyi.wechat.skill.SkillBundle;
import com.ruoyi.wechat.skill.SkillPromptBuilder;
import com.ruoyi.wechat.skill.SkillReference;
import com.ruoyi.wechat.skill.SkillResourceLoader;
import com.ruoyi.wechat.skill.SkillRoutingService;

/**
 * 分析编排 —— 把「取数 → 认人 → 清洗 → 安全兜底 → 加载知识 → 调用军师」串起来。
 *
 * <p>设计上刻意让每一步都能单独失败并给出可诊断的信息：
 * 数据源没就绪、说话人映射不确定、知识库缺失、LLM 未配置，
 * 分别对应不同的用户可操作项，而不是笼统报"分析失败"。
 */
@Service
public class AnalysisService
{
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /** 预览展示的消息条数 */
    private static final int PREVIEW_SIZE = 12;

    private final WechatDataSource dataSource;

    private final WechatMessageCleaner cleaner;

    private final ChatContextRenderer renderer;

    private final SafetyGuard safetyGuard;

    private final SkillResourceLoader skillLoader;

    private final SkillRoutingService routingService;

    private final AnalysisResultParser resultParser;

    private final LlmClient llmClient;

    public AnalysisService(WechatDataSource dataSource, WechatMessageCleaner cleaner,
            ChatContextRenderer renderer, SafetyGuard safetyGuard, SkillResourceLoader skillLoader,
            SkillRoutingService routingService, AnalysisResultParser resultParser, LlmClient llmClient)
    {
        this.dataSource = dataSource;
        this.cleaner = cleaner;
        this.renderer = renderer;
        this.safetyGuard = safetyGuard;
        this.skillLoader = skillLoader;
        this.routingService = routingService;
        this.resultParser = resultParser;
        this.llmClient = llmClient;
    }

    /**
     * 当前数据源状态，供前端在页面上直接展示环境是否就绪。
     */
    public SourceStatus status()
    {
        return dataSource.probe();
    }

    public String dataSourceMode()
    {
        return dataSource.mode();
    }

    public List<ChatSession> listSessions(int limit)
    {
        return dataSource.listSessions(limit);
    }

    /**
     * 拉取消息并给出映射候选与预览，让用户先核对再分析。
     */
    public AnalysisPreview preview(String chatName, boolean group, String startTime, String endTime, int limit)
    {
        List<RawMessage> raw = dataSource.fetchMessages(new FetchQuery(chatName, startTime, endTime, limit, 0));
        ChatIdentity inferred = ChatIdentity.infer(null, chatName, group, raw);
        CleanResult cleaned = cleaner.clean(raw, inferred, group);

        List<String> warnings = new ArrayList<>();
        if (!inferred.autoDetected())
        {
            warnings.add("无法自动确定哪个昵称是「我」，请在上方确认说话人映射后再分析。");
        }
        if (cleaned.truncated())
        {
            warnings.add(cleaned.truncatedNote());
        }
        if (!skillLoader.isReady())
        {
            warnings.add("军师知识库未加载（缺少 resources/skill/SKILL.md），分析质量会下降。");
        }
        if (!llmClient.isConfigured())
        {
            warnings.add("LLM 未配置，无法执行分析。请在 application.yml 设置 wechat.llm.* 。");
        }

        return new AnalysisPreview(chatName, group, raw.size(), firstTime(raw), lastTime(raw),
                buildCandidate(chatName, group, null, raw, inferred), previewLines(cleaned), warnings);
    }

    /**
     * 执行一次完整分析。
     *
     * @throws IdentityRequiredException 说话人映射无法自动确定且用户未提供时抛出
     */
    public AnalysisResponse analyze(AnalysisRequest request)
    {
        long start = System.currentTimeMillis();

        List<RawMessage> raw = dataSource.fetchMessages(new FetchQuery(
                request.getChatName(), request.getStartTime(), request.getEndTime(),
                request.getLimit(), 0));

        if (raw.isEmpty())
        {
            throw new com.ruoyi.wechat.datasource.WechatDataSourceException(
                    "该会话在指定范围内没有消息：" + request.getChatName(),
                    "请确认聊天名是否与数据源输出一致，或放宽时间范围");
        }

        ChatIdentity identity = resolveIdentity(request, raw);
        CleanResult cleaned = cleaner.clean(raw, identity, request.isGroup());

        // 安全兜底：放在调用 LLM 之前，命中红线就不走普通分析链路
        String allText = joinContent(cleaned) + "\n" + nullSafe(request.getGoal());
        List<String> hits = safetyGuard.detect(allText);
        if (!hits.isEmpty())
        {
            log.warn("分析请求命中安全红线：{}（会话：{}）", hits, request.getChatName());
            return new AnalysisResponse(request.getChatName(), dataSource.mode(), cleaned.size(),
                    describeIdentity(identity, request.isGroup()), List.of(), null,
                    safetyGuard.crisisMessage(hits), null, 0, 0, System.currentTimeMillis() - start);
        }

        String identityDesc = describeIdentity(identity, request.isGroup());
        String statsBlock = renderer.renderStats(cleaned);
        String chatContext = renderer.render(cleaned.messages());
        String goal = request.getGoal() == null ? "" : request.getGoal();

        // 第一步：路由，选出 1-3 篇参考资料
        List<SkillReference> refs = routingService.route(goal, statsBlock);

        // 第二步：主分析
        SkillBundle bundle = new SkillBundle(skillLoader.getSkillMd(), refs, skillLoader.getRoutingTable());
        String systemPrompt = SkillPromptBuilder.buildAnalysisSystemPrompt(bundle);
        String userPrompt = SkillPromptBuilder.buildAnalysisUserPrompt(
                describeSession(request, cleaned), identityDesc, statsBlock, goal, chatContext);

        LlmResult llmResult = llmClient.chat(List.of(
                LlmMessage.system(systemPrompt), LlmMessage.user(userPrompt)));

        AnalysisResult parsed = resultParser.parse(llmResult.content());
        if (parsed == null)
        {
            throw new IllegalStateException("军师返回的内容无法解析为约定结构，请重试或检查模型输出格式遵循能力");
        }

        List<String> refPaths = new ArrayList<>();
        for (SkillReference r : refs)
        {
            refPaths.add(r.path());
        }

        return new AnalysisResponse(request.getChatName(), dataSource.mode(), cleaned.size(), identityDesc,
                refPaths, parsed, null, llmResult.model(), llmResult.promptTokens(),
                llmResult.completionTokens(), System.currentTimeMillis() - start);
    }

    /**
     * 基于<b>已归档</b>的消息执行分析。
     *
     * <p>与 {@link #analyze} 的区别很重要：这里不碰数据源，只分析数据库里那批
     * {@code analyzed = '0'} 的记录。这是产品要求"只把未分析过的聊天记录发给 agent"的落点
     * —— 由 {@code WeChatArchiveService} 负责挑选与标记，本方法只负责"怎么分析"。
     *
     * @param session  已归档会话
     * @param messages 待分析的归档消息（已清洗过）
     * @param goal     用户诉求
     */
    public AnalysisResponse analyzeArchived(com.ruoyi.wechat.domain.WeChatSession session,
            List<com.ruoyi.wechat.domain.WeChatMessage> messages, String goal)
    {
        long start = System.currentTimeMillis();
        if (messages == null || messages.isEmpty())
        {
            throw new IllegalStateException("没有待分析的消息");
        }

        // 归档消息本身就是清洗结果，这里只做角色映射与安全检测，不重复降噪
        ChatIdentity identity = ChatIdentity.confirmed(session.getSelfUsername(),
                com.ruoyi.wechat.domain.WeChatSession.splitLabels(session.getSelfLabels()),
                com.ruoyi.wechat.domain.WeChatSession.splitLabels(session.getTargetLabels()));

        List<CleanedMessage> cleanedMessages = new ArrayList<>(messages.size());
        Map<CleanedMessage.Role, Integer> roleCounts = new java.util.EnumMap<>(CleanedMessage.Role.class);
        int seq = 0;
        StringBuilder allText = new StringBuilder();
        for (com.ruoyi.wechat.domain.WeChatMessage m : messages)
        {
            CleanedMessage.Role role = parseRole(m.getSenderRole());
            CleanedMessage.Kind kind = parseKind(m.getMsgKind());
            cleanedMessages.add(new CleanedMessage(seq++, m.getMsgTime(), m.getSenderRaw(),
                    role, m.getContent(), kind));
            roleCounts.merge(role, 1, Integer::sum);
            allText.append(m.getContent()).append('\n');
        }

        CleanResult cleaned = new CleanResult(cleanedMessages, 0, 0, 0, roleCounts, false, null);
        boolean group = session.isGroupChat();
        String identityDesc = describeIdentity(identity, group);

        // 安全兜底先于 LLM 调用
        allText.append('\n').append(goal == null ? "" : goal);
        List<String> hits = safetyGuard.detect(allText.toString());
        if (!hits.isEmpty())
        {
            log.warn("归档分析命中安全红线：{}（会话：{}）", hits, session.getDisplayName());
            return new AnalysisResponse(session.getDisplayName(), "archive", cleaned.size(),
                    identityDesc, List.of(), null, safetyGuard.crisisMessage(hits), null, 0, 0,
                    System.currentTimeMillis() - start);
        }

        String statsBlock = renderer.renderStats(cleaned);
        String chatContext = renderer.render(cleaned.messages());
        String safeGoal = goal == null ? "" : goal;

        List<SkillReference> refs = routingService.route(safeGoal, statsBlock);
        SkillBundle bundle = new SkillBundle(skillLoader.getSkillMd(), refs, skillLoader.getRoutingTable());

        String systemPrompt = SkillPromptBuilder.buildAnalysisSystemPrompt(bundle);
        String userPrompt = SkillPromptBuilder.buildAnalysisUserPrompt(
                describeArchivedSession(session, cleaned), identityDesc, statsBlock, safeGoal, chatContext);

        LlmResult llmResult = llmClient.chat(List.of(
                LlmMessage.system(systemPrompt), LlmMessage.user(userPrompt)));

        AnalysisResult parsed = resultParser.parse(llmResult.content());
        if (parsed == null)
        {
            throw new IllegalStateException("军师返回的内容无法解析为约定结构，请重试或检查模型的 JSON 遵循能力");
        }

        List<String> refPaths = new ArrayList<>();
        for (SkillReference r : refs)
        {
            refPaths.add(r.path());
        }

        return new AnalysisResponse(session.getDisplayName(), "archive", cleaned.size(), identityDesc,
                refPaths, parsed, null, llmResult.model(), llmResult.promptTokens(),
                llmResult.completionTokens(), System.currentTimeMillis() - start);
    }

    private CleanedMessage.Role parseRole(String role)
    {
        if (role == null)
        {
            return CleanedMessage.Role.UNKNOWN;
        }
        try
        {
            return CleanedMessage.Role.valueOf(role);
        }
        catch (IllegalArgumentException e)
        {
            return CleanedMessage.Role.UNKNOWN;
        }
    }

    private CleanedMessage.Kind parseKind(String kind)
    {
        if (kind == null)
        {
            return CleanedMessage.Kind.TEXT;
        }
        try
        {
            return CleanedMessage.Kind.valueOf(kind);
        }
        catch (IllegalArgumentException e)
        {
            return CleanedMessage.Kind.TEXT;
        }
    }

    private String describeArchivedSession(com.ruoyi.wechat.domain.WeChatSession session, CleanResult cleaned)
    {
        return "会话名：" + session.getDisplayName()
                + "\n类型：" + (session.isGroupChat() ? "群聊" : "私聊")
                + "\n本次分析的消息条数：" + cleaned.size()
                + "\n说明：这批消息是上次分析之后新增的部分，更早的内容此前已单独分析过。";
    }

    /**
     * 决定用哪套说话人映射：用户显式提供优先，否则尝试自动推断。
     */
    private ChatIdentity resolveIdentity(AnalysisRequest request, List<RawMessage> raw)
    {
        boolean hasUserLabels = request.getSelfLabels() != null && !request.getSelfLabels().isEmpty();
        if (hasUserLabels)
        {
            return ChatIdentity.confirmed(request.getSelfUsername(), request.getSelfLabels(),
                    request.getTargetLabels() == null ? List.of() : request.getTargetLabels());
        }

        ChatIdentity inferred = ChatIdentity.infer(request.getSelfUsername(), request.getChatName(),
                request.isGroup(), raw);
        if (inferred.autoDetected())
        {
            return inferred;
        }

        // 无法自动判断，强制走确认流程
        throw new IdentityRequiredException(
                buildCandidate(request.getChatName(), request.isGroup(), request.getSelfUsername(), raw, inferred));
    }

    private IdentityCandidate buildCandidate(String chatName, boolean group, String selfUsername,
            List<RawMessage> raw, ChatIdentity inferred)
    {
        Map<String, Integer> counts = ChatIdentity.countSenders(raw);
        List<IdentityCandidate.SenderCount> senders = new ArrayList<>();
        counts.forEach((label, count) -> senders.add(new IdentityCandidate.SenderCount(label, count)));

        String hint = inferred.autoDetected()
                ? "已自动识别说话人，请核对是否正确。"
                : "聊天记录里没有明确的「我」标记，无法自动判断哪个昵称是你。"
                        + "请手动指定，避免把整段分析建立在错误的说话人前提上。";

        return new IdentityCandidate(chatName, group, selfUsername, inferred.autoDetected(),
                senders, inferred.selfLabels(), inferred.targetLabels(), hint);
    }

    private List<String> previewLines(CleanResult cleaned)
    {
        List<String> lines = new ArrayList<>();
        List<CleanedMessage> messages = cleaned.messages();
        int from = Math.max(0, messages.size() - PREVIEW_SIZE);
        for (int i = from; i < messages.size(); i++)
        {
            CleanedMessage m = messages.get(i);
            lines.add("[" + nullSafe(m.time()) + "] " + roleLabel(m.role()) + ": " + nullSafe(m.content()));
        }
        return lines;
    }

    private String describeIdentity(ChatIdentity identity, boolean group)
    {
        String self = identity.selfLabels().isEmpty() ? "（未确认）" : String.join("、", identity.selfLabels());
        String target = identity.targetLabels().isEmpty() ? "（未确认）" : String.join("、", identity.targetLabels());
        StringBuilder sb = new StringBuilder();
        sb.append("「我」= ").append(self);
        if (!group)
        {
            sb.append("；「对方」= ").append(target);
        }
        else
        {
            sb.append("；群聊中其余发言者一律视为「群成员」，不对其做单独的关系判断");
        }
        if (!identity.autoDetected())
        {
            sb.append("（该映射由用户手动确认）");
        }
        return sb.toString();
    }

    private String describeSession(AnalysisRequest request, CleanResult cleaned)
    {
        return "会话名：" + request.getChatName()
                + "\n类型：" + (request.isGroup() ? "群聊" : "私聊")
                + "\n时间范围：" + nullSafe(request.getStartTime(), "最早") + " ~ "
                + nullSafe(request.getEndTime(), "最新")
                + "\n提供的消息条数：" + cleaned.size();
    }

    private String joinContent(CleanResult cleaned)
    {
        StringBuilder sb = new StringBuilder();
        for (CleanedMessage m : cleaned.messages())
        {
            sb.append(m.content()).append('\n');
        }
        return sb.toString();
    }

    private String roleLabel(CleanedMessage.Role role)
    {
        return switch (role)
        {
            case SELF -> "我";
            case TARGET -> "对方";
            case OTHER -> "群成员";
            case UNKNOWN -> "未知说话人";
        };
    }

    private String firstTime(List<RawMessage> raw)
    {
        return raw.isEmpty() ? null : raw.get(0).time();
    }

    private String lastTime(List<RawMessage> raw)
    {
        return raw.isEmpty() ? null : raw.get(raw.size() - 1).time();
    }

    private String nullSafe(String s)
    {
        return s == null ? "" : s;
    }

    private String nullSafe(String s, String fallback)
    {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
