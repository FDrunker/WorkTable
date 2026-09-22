package com.ruoyi.web.controller.wechat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.ruoyi.wechat.domain.AcquaintanceDateRequest;
import com.ruoyi.wechat.domain.ChatDetail;
import com.ruoyi.wechat.domain.IdentityConfirmRequest;
import com.ruoyi.wechat.domain.SessionView;
import com.ruoyi.wechat.domain.TextImportCommitRequest;
import com.ruoyi.wechat.domain.TextImportRequest;
import com.ruoyi.wechat.domain.WeChatAnalysis;
import com.ruoyi.wechat.service.WeChatArchiveService;
import com.ruoyi.wechat.service.WechatStatusService;
import com.ruoyi.wechat.service.WechatTextImportService;

/**
 * 微信聊天界面接口。
 *
 * <p><b>本控制器不再包含任何调用 agent 的接口。</b>
 * 调用 agent 的能力已从本模块整体移除，后续由其他方式接入。
 * 因此这里只剩三类只读或本地归档操作：
 * <ul>
 *   <li>环境自检与会话发现</li>
 *   <li>导入文本 / 打开会话读取聊天记录（必要时做幂等同步）</li>
 *   <li>确认说话人映射、读取已保存的分析记录</li>
 * </ul>
 *
 * <p>{@code /analysis/list} 与 {@code /analysis/{id}} 保留：它们只读数据库，
 * 用于展示此前已保存的分析结果，不产生任何外部调用。
 */
@RestController
@RequestMapping("/wechat")
public class WechatChatController extends BaseController
{
    /**
     * agent 调用能力开关。
     *
     * <p>置为 false 时 {@code /chat/analyze} 返回明确的"未启用"提示，
     * 而不是让前端以为请求成功或静默失败。
     */
    private static final boolean AGENT_INVOCATION_ENABLED = false;

    private final WeChatArchiveService archiveService;

    private final WechatStatusService statusService;

    private final WechatTextImportService textImportService;

    public WechatChatController(WeChatArchiveService archiveService, WechatStatusService statusService,
            WechatTextImportService textImportService)
    {
        this.archiveService = archiveService;
        this.statusService = statusService;
        this.textImportService = textImportService;
    }

    /**
     * 数据源自检。
     */
    @PreAuthorize("@ss.hasPermi('wechat:session:list')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        SourceStatus status = statusService.status();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ready", status.ready());
        data.put("mode", status.mode());
        data.put("message", status.message());
        // 明确告知前端调用 agent 的能力当前不可用，避免用户反复点击
        data.put("agentEnabled", AGENT_INVOCATION_ENABLED);
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
        Set<String> archivedUsernames = new LinkedHashSet<>();
        for (SessionView s : archived)
        {
            archivedUsernames.add(s.chatUsername());
            merged.add(toMap(s));
        }

        // 数据源不可用时只是少一批候选，不应该让整个列表接口失败
        try
        {
            List<ChatSession> discovered = statusService.discoverSessions(
                    Math.min(Math.max(discoverLimit, 1), 200));
            for (ChatSession c : discovered)
            {
                if (archivedUsernames.contains(c.username()))
                {
                    continue;
                }
                merged.add(toDiscoveredMap(c));
            }
        }
        catch (Exception e)
        {
            logger.warn("读取数据源会话列表失败，仅返回已归档会话：{}", e.getMessage());
        }

        return AjaxResult.success(merged);
    }

    /**
     * 解析粘贴文本并返回发送者候选，不写数据库。
     */
    @PreAuthorize("@ss.hasPermi('wechat:message:sync')")
    @PostMapping("/text/preview")
    public AjaxResult previewText(@RequestBody TextImportRequest request)
    {
        return AjaxResult.success(textImportService.previewText(getUserId(), request.getText()));
    }

    /**
     * 用户选择自己的昵称后，将预览文本归档到对方昵称对应的会话。
     */
    @PreAuthorize("@ss.hasPermi('wechat:message:sync')")
    @Log(title = "微信文本导入", businessType = BusinessType.OTHER)
    @PostMapping("/text/commit")
    public AjaxResult commitText(@RequestBody TextImportCommitRequest request)
    {
        ChatDetail detail = textImportService.commitText(getUserId(), request.getImportId(),
                request.getSelfLabel(), getUsername());
        return AjaxResult.success(detail);
    }

    /**
     * 打开会话：按需同步后返回聊天记录。
     *
     * <p>{@code sync=true} 时会先拉取一次新消息（幂等），不涉及任何外部模型调用。
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
     * 手动触发同步（只取数落库）。
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
     * 设置或清除会话的认识日期。
     *
     * <p>接口现在就返回 acquaintanceDays。好感度、我的发挥和下一步建议同样是
     * 响应字段，当前为空，后续模型实现只需填充字段而不用再改前端协议。
     */
    @PreAuthorize("@ss.hasPermi('wechat:identity:edit')")
    @Log(title = "微信认识日期", businessType = BusinessType.UPDATE)
    @PostMapping("/session/acquaintance-date")
    public AjaxResult updateAcquaintanceDate(@RequestBody AcquaintanceDateRequest request)
    {
        SessionView view = archiveService.updateAcquaintanceDate(getUserId(), request.getSessionId(),
                request.getAcquaintanceDate(), getUsername());
        return AjaxResult.success(view);
    }

    /**
     * 删除会话及其消息和历史分析记录。
     */
    @PreAuthorize("@ss.hasPermi('wechat:session:remove')")
    @Log(title = "微信会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/session/{sessionId}")
    public AjaxResult removeSession(@PathVariable Long sessionId)
    {
        return toAjax(archiveService.removeSession(getUserId(), sessionId));
    }

    /**
     * 确认说话人映射，并回写历史消息的角色。
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
     * 分析入口占位。
     *
     * <p>调用 agent 的代码已移除，这里显式返回未启用，
     * 而不是留一个会 500 或静默成功的空接口。接入其他方式后在此处替换实现。
     */
    @PreAuthorize("@ss.hasPermi('wechat:analysis:run')")
    @PostMapping("/chat/analyze")
    public AjaxResult analyze()
    {
        if (!AGENT_INVOCATION_ENABLED)
        {
            return AjaxResult.error("分析功能未启用：调用 agent 的能力已从本模块移除");
        }
        return AjaxResult.error("分析功能未实现");
    }

    /**
     * 已保存的分析记录（只读，不触发任何外部调用）。
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
     * 分析详情（含 prompt 快照）。
     */
    @PreAuthorize("@ss.hasPermi('wechat:analysis:query')")
    @GetMapping("/analysis/{analysisId}")
    public AjaxResult analysisDetail(@PathVariable Long analysisId)
    {
        return AjaxResult.success(archiveService.getAnalysis(getUserId(), analysisId));
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
        item.put("affinityScore", s.affinityScore());
        item.put("acquaintanceDate", s.acquaintanceDate());
        item.put("acquaintanceDays", s.acquaintanceDays());
        item.put("myPerformanceScore", s.myPerformanceScore());
        item.put("nextStepSuggestion", s.nextStepSuggestion());
        item.put("archived", true);
        return item;
    }

    private Map<String, Object> toDiscoveredMap(ChatSession c)
    {
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
        item.put("affinityScore", null);
        item.put("acquaintanceDate", null);
        item.put("acquaintanceDays", null);
        item.put("myPerformanceScore", null);
        item.put("nextStepSuggestion", null);
        item.put("archived", false);
        return item;
    }
}
