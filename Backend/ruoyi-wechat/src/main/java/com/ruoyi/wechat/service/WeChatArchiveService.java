package com.ruoyi.wechat.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.wechat.clean.CleanResult;
import com.ruoyi.wechat.clean.CleanedMessage;
import com.ruoyi.wechat.clean.WechatMessageCleaner;
import com.ruoyi.wechat.config.WechatProperties;
import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.FetchQuery;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.domain.ChatDetail;
import com.ruoyi.wechat.domain.ChatMessageView;
import com.ruoyi.wechat.domain.SessionView;
import com.ruoyi.wechat.domain.WeChatAnalysis;
import com.ruoyi.wechat.domain.WeChatMessage;
import com.ruoyi.wechat.domain.WeChatSession;
import com.ruoyi.wechat.identity.ChatIdentity;
import com.ruoyi.wechat.mapper.WeChatAnalysisMapper;
import com.ruoyi.wechat.mapper.WeChatMessageMapper;
import com.ruoyi.wechat.mapper.WeChatSessionMapper;

/**
 * 会话归档与分析持久化服务。
 *
 * <p>对应三条产品要求：
 * <ol>
 *   <li><b>点进会话只显示聊天记录</b> —— {@link #openChat} 只读本地归档，
 *       不调用任何 LLM。它可能触发一次数据源同步，但同步只是取数落库。</li>
 *   <li><b>点分析才发送未分析过的记录</b> —— {@link #analyze} 只取
 *       {@code analyzed = '0'} 的消息，分析成功后把这批消息标记为已分析。</li>
 *   <li><b>未点击分析时读历史分析记录</b> —— {@link #listAnalysisHistory}
 *       直接读库，不触发新分析。</li>
 * </ol>
 *
 * <p>全局唯一的 {@code userId} 过滤是刻意的：会话主键是
 * {@code (user_id, chat_username)}，所有查询都必须带上归属校验，
 * 否则不同用户之间会互相读到对方的聊天记录。
 */
@Service
public class WeChatArchiveService
{
    private static final Logger log = LoggerFactory.getLogger(WeChatArchiveService.class);

    private static final String DEL_FLAG_NORMAL = "0";

    /** 会话展示名长度上限（归档表该列 128，留足余量） */
    private static final int NAME_MAX_LENGTH = 60;

    private final WechatDataSource dataSource;

    private final WechatMessageCleaner cleaner;

    private final WechatProperties properties;

    private final WeChatSessionMapper sessionMapper;

    private final WeChatMessageMapper messageMapper;

    private final WeChatAnalysisMapper analysisMapper;

    public WeChatArchiveService(WechatDataSource dataSource, WechatMessageCleaner cleaner,
            WechatProperties properties, WeChatSessionMapper sessionMapper,
            WeChatMessageMapper messageMapper, WeChatAnalysisMapper analysisMapper)
    {
        this.dataSource = dataSource;
        this.cleaner = cleaner;
        this.properties = properties;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.analysisMapper = analysisMapper;
    }

    /**
     * 列出该用户已有的会话，并附带数据源里发现的会话（尚未同步的）。
     *
     * <p>已归档的会话优先展示，因为只有它们才有聊天记录可看。
     */
    public List<SessionView> listSessions(Long userId)
    {
        List<WeChatSession> archived = sessionMapper.selectSessionsByUser(userId, null);
        List<SessionView> result = new ArrayList<>(archived.size());
        for (WeChatSession s : archived)
        {
            result.add(toView(s));
        }
        return result;
    }

    /**
     * 从数据源列出可同步的会话（用于"发现新会话"入口）。
     */
    public List<ChatSession> discoverSessions(int limit)
    {
        return dataSource.listSessions(limit);
    }

    /**
     * 打开会话：按需同步（幂等），然后只读本地归档返回聊天记录。
     *
     * <p>同步失败（例如数据源未就绪）<b>不应该让聊天记录打不开</b>：
     * 已经归档的内容照常展示，只在日志与返回值里体现同步未完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatDetail openChat(Long userId, Long sessionId, String chatUsername, String displayName,
            boolean group, int offset, int limit, boolean sync, String operator)
    {
        WeChatSession session = resolveSession(userId, sessionId, chatUsername, displayName, group, operator);

        if (sync)
        {
            try
            {
                syncSession(userId, session, operator);
                session = sessionMapper.selectById(session.getSessionId());
            }
            catch (Exception e)
            {
                // 同步失败不阻断浏览：已归档内容仍然可读
                log.warn("同步会话失败，将展示已归档内容。sessionId={}, 原因={}",
                        session.getSessionId(), e.getMessage());
            }
        }

        int effectiveLimit = limit <= 0 ? properties.getClean().getMessagePageSize() : Math.min(limit, 500);
        int effectiveOffset = Math.max(offset, 0);
        int total = messageMapper.countMessages(session.getSessionId(), null);
        List<WeChatMessage> messages = messageMapper.selectMessages(session.getSessionId(), null,
                effectiveOffset, effectiveLimit);

        List<ChatMessageView> views = new ArrayList<>(messages.size());
        for (WeChatMessage m : messages)
        {
            views.add(new ChatMessageView(m.getMessageId(), m.getMsgTime(), m.getMsgDate(),
                    m.getSenderRole(), m.getSenderRaw(), m.getContent(), m.getMsgKind(),
                    "1".equals(m.getAnalyzed())));
        }

        return new ChatDetail(toView(session), views, total, effectiveOffset, effectiveLimit);
    }

    /**
     * 把数据源的消息同步进归档。重复调用是幂等的。
     *
     * @return 本次实际新增的条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncSession(Long userId, WeChatSession session, String operator)
    {
        int batchSize = properties.getCli().getBatchSize();
        List<RawMessage> raw = dataSource.fetchMessages(
                FetchQuery.of(session.getChatUsername(), batchSize));

        if (raw.isEmpty())
        {
            log.info("会话 {} 未从数据源取到消息", session.getChatUsername());
            return 0;
        }

        // 用已确认的映射；未确认则尝试自动推断，仍不确定就全部标 UNKNOWN，
        // 但消息照常入库 —— 否则用户连记录都看不到，更没法确认映射。
        ChatIdentity identity = buildIdentity(session);

        CleanResult cleaned = cleaner.clean(raw, identity, session.groupChat());

        List<WeChatMessage> rows = new ArrayList<>(cleaned.messages().size());
        for (CleanedMessage m : cleaned.messages())
        {
            WeChatMessage row = new WeChatMessage();
            row.setSessionId(session.getSessionId());
            row.setUserId(userId);
            row.setMsgTime(m.time());
            row.setMsgDate(extractDate(m.time()));
            row.setSenderRaw(m.rawSender());
            row.setSenderRole(m.role().name());
            row.setContent(m.content());
            row.setContentMd5(md5(m.content()));
            row.setMsgKind(m.kind().name());
            rows.add(row);
        }

        int inserted = 0;
        // 分批插入，避免单条 SQL 过大超出 max_allowed_packet
        for (int i = 0; i < rows.size(); i += 200)
        {
            List<WeChatMessage> slice = rows.subList(i, Math.min(i + 200, rows.size()));
            inserted += messageMapper.batchInsertIgnore(slice);
        }

        // 首次同步时把自动推断出的映射固化下来，后续确认可以直接覆盖
        if (!session.identityConfirmed() && identity.autoDetected())
        {
            updateIdentity(session, identity.selfUsername(), identity.selfLabels(),
                    identity.targetLabels(), false, operator);
        }

        String latestTime = cleaned.messages().isEmpty() ? session.getSyncedUntil()
                : cleaned.messages().get(cleaned.messages().size() - 1).time();
        WeChatSession update = new WeChatSession();
        update.setSessionId(session.getSessionId());
        update.setSyncedUntil(latestTime);
        update.setLastSyncTime(new Date());
        update.setUpdateBy(operator);
        sessionMapper.updateSession(update);
        sessionMapper.refreshCounters(session.getSessionId());

        log.info("同步会话 {} 完成：拉取 {} 条，新增 {} 条，过滤 {} 条，折叠 {} 条",
                session.getChatUsername(), raw.size(), inserted, cleaned.droppedCount(),
                cleaned.collapsedCount());
        return inserted;
    }

    /**
     * 修改会话展示名。
     *
     * <p>只改 {@code display_name}，会话标识保持不变 —— 标识决定归档消息归属，
     * 跟着改名会让历史记录看起来像是另一个会话的。
     * 另外，导入时若会话已存在（同名文本再次粘贴）不会覆盖展示名，
     * 所以这里改的名字不会被后续导入冲掉。
     *
     * @param displayName 新名称，首尾空白会被裁掉
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionView renameSession(Long userId, Long sessionId, String displayName, String operator)
    {
        requireSession(userId, sessionId);

        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty())
        {
            throw new IllegalArgumentException("会话名称不能为空");
        }
        if (name.length() > NAME_MAX_LENGTH)
        {
            throw new IllegalArgumentException("会话名称过长：上限 " + NAME_MAX_LENGTH + " 个字符");
        }

        WeChatSession update = new WeChatSession();
        update.setSessionId(sessionId);
        update.setDisplayName(name);
        update.setUpdateBy(operator);
        sessionMapper.updateSession(update);

        return toView(sessionMapper.selectById(sessionId));
    }

    /**
     * 确认说话人映射，并把角色回写到已归档的消息上。
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionView confirmIdentity(Long userId, Long sessionId, String selfUsername,
            List<String> selfLabels, List<String> targetLabels, String operator)
    {
        WeChatSession session = requireSession(userId, sessionId);
        updateIdentity(session, selfUsername, selfLabels, targetLabels, true, operator);

        // 先清掉旧映射结果，再按新映射回写，避免改映射后残留错误的角色
        messageMapper.updateSenderRoleByLabel(sessionId, null, "UNKNOWN");
        for (String label : selfLabels)
        {
            messageMapper.updateSenderRoleByLabel(sessionId, label, "SELF");
        }
        for (String label : targetLabels)
        {
            messageMapper.updateSenderRoleByLabel(sessionId, label, "TARGET");
        }

        return toView(sessionMapper.selectById(sessionId));
    }

    /**
     * 历史分析记录，按时间倒序。不触发任何新分析。
     */
    public List<WeChatAnalysis> listAnalysisHistory(Long userId, Long sessionId, int limit)
    {
        requireSession(userId, sessionId);
        return analysisMapper.selectBySession(sessionId, limit <= 0 ? 20 : Math.min(limit, 100));
    }

    /**
     * 分析详情（含 prompt 快照，用于复现"为什么这么答"）。
     */
    public WeChatAnalysis getAnalysis(Long userId, Long analysisId)
    {
        WeChatAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null)
        {
            throw new IllegalArgumentException("分析记录不存在：" + analysisId);
        }
        if (!analysis.getUserId().equals(userId))
        {
            throw new IllegalArgumentException("无权访问该分析记录");
        }
        return analysis;
    }

    /**
     * 删除分析记录（逻辑删除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int removeAnalysis(Long userId, Long[] analysisIds)
    {
        if (analysisIds == null || analysisIds.length == 0)
        {
            return 0;
        }
        return analysisMapper.deleteByIds(analysisIds, userId);
    }

    /**
     * 由标签集合构造映射；已确认的映射优先。
     */
    private ChatIdentity buildIdentity(WeChatSession session)
    {
        if (session.identityConfirmed())
        {
            return ChatIdentity.confirmed(session.getSelfUsername(),
                    WeChatSession.splitLabels(session.getSelfLabels()),
                    WeChatSession.splitLabels(session.getTargetLabels()));
        }
        // 未确认时只能用数据源的 selfUsername 尝试推断，不做任何猜测性赋值
        return new ChatIdentity(session.getSelfUsername(), List.of(), List.of(), false);
    }

    private void updateIdentity(WeChatSession session, String selfUsername, List<String> selfLabels,
            List<String> targetLabels, boolean confirmed, String operator)
    {
        WeChatSession update = new WeChatSession();
        update.setSessionId(session.getSessionId());
        update.setSelfUsername(selfUsername == null ? "" : selfUsername);
        update.setSelfLabels(WeChatSession.joinLabels(selfLabels));
        update.setTargetLabels(WeChatSession.joinLabels(targetLabels));
        update.setIdentityConfirmed(confirmed ? "1" : "0");
        update.setUpdateBy(operator);
        sessionMapper.updateSession(update);
    }

    /**
     * 定位（必要时创建）会话。
     *
     * <p>支持两种入口：已有 sessionId，或首次从数据源发现的 chatUsername。
     */
    private WeChatSession resolveSession(Long userId, Long sessionId, String chatUsername,
            String displayName, boolean group, String operator)
    {
        if (sessionId != null)
        {
            return requireSession(userId, sessionId);
        }
        if (chatUsername == null || chatUsername.isBlank())
        {
            throw new IllegalArgumentException("必须提供 sessionId 或 chatUsername");
        }

        WeChatSession existing = sessionMapper.selectByUserAndChat(userId, chatUsername);
        if (existing != null)
        {
            return existing;
        }

        WeChatSession created = new WeChatSession();
        created.setUserId(userId);
        created.setChatUsername(chatUsername);
        created.setDisplayName(displayName == null || displayName.isBlank() ? chatUsername : displayName);
        created.setIsGroup(group ? "1" : "0");
        created.setSelfUsername("");
        created.setSelfLabels("");
        created.setTargetLabels("");
        created.setIdentityConfirmed("0");
        created.setMsgCount(0);
        created.setUnanalyzedCount(0);
        created.setCreateBy(operator);
        sessionMapper.insertSession(created);
        log.info("归档新会话：userId={}, chat={}", userId, chatUsername);
        return created;
    }

    private WeChatSession requireSession(Long userId, Long sessionId)
    {
        WeChatSession session = sessionMapper.selectById(sessionId);
        if (session == null)
        {
            throw new IllegalArgumentException("会话不存在：" + sessionId);
        }
        if (!session.getUserId().equals(userId))
        {
            // 归属校验：防止越权读取他人聊天记录
            throw new IllegalArgumentException("无权访问该会话");
        }
        return session;
    }

    private SessionView toView(WeChatSession s)
    {
        int analysisCount = analysisMapper.selectBySession(s.getSessionId(), 1000).size();
        String lastTime = null;
        String lastMessage = null;

        int total = messageMapper.countMessages(s.getSessionId(), null);
        if (total > 0)
        {
            // 消息按 message_id 升序，因此"最后一条"就是偏移到 total-1 的那条
            List<WeChatMessage> tail = messageMapper.selectMessages(s.getSessionId(), null, total - 1, 1);
            if (!tail.isEmpty())
            {
                lastTime = tail.get(0).getMsgTime();
                lastMessage = truncate(tail.get(0).getContent(), 40);
            }
        }

        return new SessionView(s.getSessionId(), s.getChatUsername(), s.getDisplayName(), s.groupChat(),
                s.identityConfirmed(), WeChatSession.splitLabels(s.getSelfLabels()),
                WeChatSession.splitLabels(s.getTargetLabels()),
                s.getMsgCount() == null ? 0 : s.getMsgCount(),
                s.getUnanalyzedCount() == null ? 0 : s.getUnanalyzedCount(),
                analysisCount, lastTime, lastMessage,
                s.getLastSyncTime() == null ? null : s.getLastSyncTime().toString());
    }

    private String extractDate(String time)
    {
        return (time == null || time.length() < 10) ? "" : time.substring(0, 10);
    }

    private String md5(String content)
    {
        String text = content == null ? "" : content;
        return org.springframework.util.DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
    }

    private String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
