package com.ruoyi.web.controller.wechat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.SourceStatus;
import com.ruoyi.wechat.domain.AnalysisTriggerRequest;
import com.ruoyi.wechat.domain.AnalysisTriggerResult;
import com.ruoyi.wechat.domain.ChatDetail;
import com.ruoyi.wechat.domain.IdentityConfirmRequest;
import com.ruoyi.wechat.domain.SessionView;
import com.ruoyi.wechat.domain.WeChatAnalysis;
import com.ruoyi.wechat.service.AnalysisService;
import com.ruoyi.wechat.service.WeChatArchiveService;

/**
 * 微信聊天界面接口。
 *
 * <p>严格对应界面上的三个动作，且职责分离：
 * <ul>
 *   <li><b>打开会话</b> {@code GET /chat/open} —— 只读归档的聊天记录，不调用 LLM</li>
 *   <li><b>历史分析</b> {@code GET /analysis/list} —— 读库，不调用 LLM</li>
 *   <li><b>点击分析</b> {@code POST /chat/analyze} —— 只有这条路径会调 LLM，
 *       且只发送 {@code analyzed = '0'} 的消息</li>
 * </ul>
 */
@RestController
@RequestMapping("/wechat")
public class WechatChatController extends BaseController
{
    /** 单次分析最多处理多少条未分析消息的硬上限 */
    private static final int ANALYSIS_LIMIT_HARD_CAP = 2000;

    private final WeChatArchiveService archiveService;

    private final AnalysisService analysisService;

    public WechatChatController(WeChatArchiveService archiveService, AnalysisService analysisService)
    {
        this.archiveService = archiveService;
        this.analysisService = analysisService;
    }

    /**
     * 数据源与知识库自检。
     */
    @PreAuthorize("@ss.hasPermi('wechat:session:list')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        SourceStatus status = analysisService.status();
        Map<String, Object> data = new HashMap<>();
        data.put("ready", status.ready());
        data.put("mode", status.mode());
        data.put("message", status.message());
        return AjaxResult.success(data);
    }

    /**
     * 会话列表：已归档的在前，数据源里尚未归档的补充在后。
     *
     * <p>补充未归档会话是必要的：全新安装时归档表是空的，
     * 如果只返回已归档会话，左侧栏会一片空白，用户没有任何入口可以点。
     */
    @PreAuthorize("@ss.hasPermi('wechat:session:list')")
    @GetMapping("/sessions")
    public AjaxResult sessions(@RequestParam(defaultValue = "50") int discoverLimit)
    {
        Long userId = getUserId();
        List<SessionView> archived = archiveService.listSessions(userId);

        List<Map<String, Object>> merged = new ArrayList<>();
        java.util.Set<String> archivedUsernames = new java.util.HashSet<>();
        for (SessionView s : archived)
        {
            archivedUsernames.add(s.chatUsername());
            merged.add(toMap(s));
        }

        // 数据源不可用时只是少一批候选，不应该让整个列表接口失败
        try
        {
            List<ChatSession> discovered = archiveService.discoverSessions(
                    Math.min(Math.max(discoverLimit, 1), 200));
            for (ChatSession c : discovered)
            {
                if (archivedUsernames.contains(c.username()))
                {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sessionId", null);
                item.put("chatUsername", c.username());
                item.put("displayName", c.displayName());
                item.put("group", c.group());
                item.put("identityConfirmed", false);
                item.put("selfLabels", List.of());
                item.put("targetLabels", List.of());
                item.put("msgCount", 0);
                item.put("unanalyzedCount", 0);
                item.put("analysisCount", 0);
                item.put("lastTime", c.lastTime());
                item.put("lastMessage", c.lastMessage());
                item.put("lastSyncTime", null);
                item.put("archived", false);
                merged.add(item);
            }
        }
        catch (Exception e)
        {
            logger.warn("读取数据源会话列表失败，仅返回已归档会话：{}", e.getMessage());
        }

        return AjaxResult.success(merged);
    }

    /**
     * 打开会话：按需同步后返回聊天记录。
     *
     * <p>{@code sync=true} 时会先拉取一次新消息（幂等），但<b>不会</b>触发任何分析。
     */
    @PreAuthorize("@ss.hasPermi('wechat:message:list')")
    @GetMapping("/chat/open")
    public AjaxResult openChat(@RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String chatUsername,
            @RequestParam(required = false) String displayName,
            @RequestParam(defaultValue = "false") boolean group,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "0") int limit,
            @RequestParam(defaultValue = "true") boolean sync)
    {
        ChatDetail detail = archiveService.openChat(getUserId(), sessionId, chatUsername, displayName,
                group, offset, limit, sync, getUsername());
        return AjaxResult.success(detail);
    }

    /**
     * 手动触发同步（不分析）。
     */
    @PreAuthorize("@ss.hasPermi('wechat:message:sync')")
    @Log(title = "微信记录同步", businessType = BusinessType.OTHER)
    @PostMapping("/chat/sync")
    public AjaxResult sync(@RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String chatUsername,
            @RequestParam(required = false) String displayName,
            @RequestParam(defaultValue = "false") boolean group)
    {
        ChatDetail detail = archiveService.openChat(getUserId(), sessionId, chatUsername, displayName,
                group, 0, 1, true, getUsername());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", detail.session());
        data.put("msgCount", detail.total());
        return AjaxResult.success(data);
    }

    /**
     * 确认说话人映射。
     */
    @PreAuthorize("@ss.hasPermi('wechat:identity:edit')")
    @Log(title = "微信说话人映射", businessType = BusinessType.UPDATE)
    @PostMapping("/identity/confirm")
    public AjaxResult confirmIdentity(@RequestBody IdentityConfirmRequest request)
    {
        SessionView view = archiveService.confirmIdentity(getUserId(), request.getSessionId(),
                request.getSelfUsername(), request.getSelfLabels(), request.getTargetLabels(), getUsername());
        return AjaxResult.success(view);
    }

    /**
     * 执行分析：<b>只把未分析过的聊天记录</b>送给军师，结果落库。
     *
     * <p>返回体里带 {@code remaining}，前端据此提示"还有 N 条未分析，可再次点击分析"。
     */
    @PreAuthorize("@ss.hasPermi('wechat:analysis:run')")
    @Log(title = "微信聊天分析", businessType = BusinessType.OTHER)
    @PostMapping("/chat/analyze")
    public AjaxResult analyze(@RequestBody AnalysisTriggerRequest request)
    {
        if (request.getSessionId() == null)
        {
            return AjaxResult.error("必须指定 sessionId");
        }
        AnalysisTriggerResult result = archiveService.analyze(getUserId(), request.getSessionId(),
                request.getGoal(), getUsername(),
                (session, messages, goal) -> analysisService.analyzeArchived(session, messages, goal));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis", result.analysis());
        data.put("analyzedCount", result.analyzedCount());
        data.put("remaining", result.remaining());
        data.put("hasMore", result.hasMore());
        return AjaxResult.success(data);
    }

    /**
     * 历史分析记录（不含 prompt 快照，避免响应过大）。
     */
    @PreAuthorize("@ss.hasPermi('wechat:analysis:query')")
    @GetMapping("/analysis/list")
    public AjaxResult analysisList(@RequestParam Long sessionId, @RequestParam(defaultValue = "20") int limit)
    {
        List<WeChatAnalysis> list = archiveService.listAnalysisHistory(getUserId(), sessionId, limit);
        // prompt_snapshot 体积可能上百 KB，列表接口不带出来
        list.forEach(a -> a.setPromptSnapshot(null));
        return AjaxResult.success(list);
    }

    /**
     * 分析详情（含 prompt 快照，用于复现）。
     */
    @PreAuthorize("@ss.hasPermi('wechat:analysis:query')")
    @GetMapping("/analysis/{analysisId}")
    public AjaxResult analysisDetail(@PathVariable Long analysisId)
    {
        WeChatAnalysis analysis = archiveService.getAnalysis(getUserId(), analysisId);
        return AjaxResult.success(analysis);
    }

    /**
     * 删除分析记录。
     */
    @PreAuthorize("@ss.hasPermi('wechat:analysis:remove')")
    @Log(title = "微信分析记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/analysis/{analysisIds}")
    public AjaxResult removeAnalysis(@PathVariable Long[] analysisIds)
    {
        return toAjax(archiveService.removeAnalysis(getUserId(), analysisIds));
    }

    private Map<String, Object> toMap(SessionView s)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sessionId", s.sessionId());
        item.put("chatUsername", s.chatUsername());
        item.put("displayName", s.displayName());
        item.put("group", s.group());
        item.put("identityConfirmed", s.identityConfirmed());
        item.put("selfLabels", s.selfLabels());
        item.put("targetLabels", s.targetLabels());
        item.put("msgCount", s.msgCount());
        item.put("unanalyzedCount", s.unanalyzedCount());
        item.put("analysisCount", s.analysisCount());
        item.put("lastTime", s.lastTime());
        item.put("lastMessage", s.lastMessage());
        item.put("lastSyncTime", s.lastSyncTime());
        item.put("archived", true);
        return item;
    }
}
