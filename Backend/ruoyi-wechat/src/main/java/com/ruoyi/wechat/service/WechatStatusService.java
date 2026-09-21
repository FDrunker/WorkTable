package com.ruoyi.wechat.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.SourceStatus;
import com.ruoyi.wechat.datasource.WechatDataSource;

/**
 * 数据源状态与发现服务。
 *
 * <p>把探测与发现这类只读能力单独放一个服务，与归档写入职责分开。
 * 本模块不包含任何调用 agent 的逻辑。
 */
@Service
public class WechatStatusService
{
    private final WechatDataSource dataSource;

    public WechatStatusService(WechatDataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    /**
     * 数据源自检。前端据此提示"命令找不到"这类环境问题。
     */
    public SourceStatus status()
    {
        return dataSource.probe();
    }

    public String dataSourceMode()
    {
        return dataSource.mode();
    }

    /**
     * 从数据源发现可同步的会话。
     */
    public List<ChatSession> discoverSessions(int limit)
    {
        return dataSource.listSessions(limit);
    }
}
