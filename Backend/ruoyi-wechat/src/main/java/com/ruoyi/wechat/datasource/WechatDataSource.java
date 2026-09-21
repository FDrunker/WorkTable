package com.ruoyi.wechat.datasource;

import java.util.List;

/**
 * 微信数据源抽象 — 整个模块<b>唯一</b>需要随数据获取方式变化而替换的接缝。
 *
 * <p>当前提供三种实现：
 * <ul>
 *   <li>{@code MockWechatDataSource} — 内置样例，用于联调与自动化测试</li>
 *   <li>{@code CliWechatDataSource} — 调用本地 wechat-cli 命令行</li>
 *   <li>{@code UploadWechatDataSource} — 解析用户上传的聊天导出文件</li>
 * </ul>
 *
 * <p>实现类必须保证：<b>不修改任何本地数据，只读</b>。
 */
public interface WechatDataSource
{
    /**
     * 数据源标识，用于日志与前端展示（如 {@code cli}、{@code mock}、{@code upload}）。
     */
    String mode();

    /**
     * 探测数据源是否就绪。
     *
     * <p>实现应当做<b>轻量</b>检查（命令是否存在、配置是否齐全），
     * 不要在这里做全量扫描，否则前端"同步"按钮前的状态展示会卡住。
     */
    SourceStatus probe();

    /**
     * 列出最近会话。
     *
     * @param limit 条数
     * @return 按最后消息时间倒序
     */
    List<ChatSession> listSessions(int limit);

    /**
     * 拉取指定聊天的一批消息。
     *
     * <p>返回结果按时间<b>升序</b>。数据源本身可能内部按倒序查询，
     * 实现负责归一化为升序，调用方不必关心。
     *
     * @return 消息列表；无法找到聊天对象时抛出 {@link WechatDataSourceException}
     */
    List<RawMessage> fetchMessages(FetchQuery query);
}
