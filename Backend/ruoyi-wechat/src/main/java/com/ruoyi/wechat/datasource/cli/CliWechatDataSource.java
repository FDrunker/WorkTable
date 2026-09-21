package com.ruoyi.wechat.datasource.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ruoyi.wechat.config.WechatProperties;
import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.FetchQuery;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.SourceStatus;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSourceException;

/**
 * 调用本地 wechat-cli 命令行的数据源。
 *
 * <p>我们把 wechat-cli 当成一个<b>只读的外部工具</b>：通过 {@code ProcessBuilder}
 * 执行其子命令并解析 JSON 输出，不触碰它的数据库与密钥文件。
 *
 * <p>退出码语义来自源码（{@code click.echo(..., err=True); ctx.exit(n)}）：
 * <ul>
 *   <li>0 — 成功</li>
 *   <li>1 — 找不到聊天对象 / 找不到消息记录</li>
 *   <li>2 — 参数无效（时间格式、分页非法）</li>
 *   <li>3 — 无法解密 session.db</li>
 * </ul>
 */
public class CliWechatDataSource implements WechatDataSource
{
    private static final Logger log = LoggerFactory.getLogger(CliWechatDataSource.class);

    /** 与源码 {@code wechat_cli/core/messages.py} 的 _QUERY_LIMIT_MAX 保持一致 */
    private static final int CLI_MAX_LIMIT = 500;

    private final WechatProperties properties;

    private final ProcessRunner runner = new ProcessRunner();

    /** 解析一次后缓存，避免每次调用都扫 PATH */
    private volatile CliExecutableResolver.Resolution resolution;

    public CliWechatDataSource(WechatProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public String mode()
    {
        return "cli";
    }

    @Override
    public SourceStatus probe()
    {
        CliExecutableResolver.Resolution res = resolveExecutable();
        if (!res.found())
        {
            // 未就绪不是异常，是一种正常状态：前端展示修复指引即可
            return SourceStatus.notReady(mode(), res.failure());
        }

        try
        {
            // 用 --limit 1 做一次真实调用验证端到端可通
            ProcessRunner.ProcessResult result = execute("sessions", "--limit", "1");
            if (result.exitCode() != 0)
            {
                return SourceStatus.notReady(mode(), describeFailure("sessions", result));
            }
            int count = CliOutputParser.parseSessions(result.stdout()).size();
            return SourceStatus.ok(mode(), "wechat-cli 可用（" + res.path() + "，来源：" + res.source()
                    + "，试调用返回 " + count + " 条会话）");
        }
        catch (WechatDataSourceException e)
        {
            return SourceStatus.notReady(mode(), e.getMessage());
        }
        catch (Exception e)
        {
            return SourceStatus.notReady(mode(), "调用 wechat-cli 失败：" + e.getMessage());
        }
    }

    @Override
    public List<ChatSession> listSessions(int limit)
    {
        ProcessRunner.ProcessResult result = execute("sessions", "--limit", String.valueOf(limit));
        requireSuccess("sessions", result);
        return CliOutputParser.parseSessions(result.stdout());
    }

    @Override
    public List<RawMessage> fetchMessages(FetchQuery query)
    {
        if (query.chatName() == null || query.chatName().isBlank())
        {
            throw new WechatDataSourceException("聊天名不能为空");
        }

        int effectiveLimit = Math.min(query.limit() <= 0 ? 50 : query.limit(), CLI_MAX_LIMIT);

        List<String> args = new ArrayList<>();
        args.add("history");
        args.add(query.chatName());
        args.add("--limit");
        args.add(String.valueOf(effectiveLimit));
        if (query.offset() > 0)
        {
            args.add("--offset");
            args.add(String.valueOf(query.offset()));
        }
        if (query.startTime() != null && !query.startTime().isBlank())
        {
            args.add("--start-time");
            args.add(query.startTime());
        }
        if (query.endTime() != null && !query.endTime().isBlank())
        {
            args.add("--end-time");
            args.add(query.endTime());
        }
        args.add("--format");
        args.add("json");

        ProcessRunner.ProcessResult result = execute(args.toArray(new String[0]));
        requireSuccess("history", result);
        return CliOutputParser.parseHistory(result.stdout());
    }

    /**
     * 执行一次 wechat-cli 子命令。
     */
    private ProcessRunner.ProcessResult execute(String... args) throws WechatDataSourceException
    {
        CliExecutableResolver.Resolution res = resolveExecutable();
        if (!res.found())
        {
            throw new WechatDataSourceException("wechat-cli 不可用", res.failure());
        }
        try
        {
            List<String> command = ProcessRunner.command(res.path(), args);
            return runner.run(command, properties.getCli().getTimeoutSeconds(),
                    properties.getCli().getCharset());
        }
        catch (IOException e)
        {
            throw new WechatDataSourceException("执行 wechat-cli 失败：" + e.getMessage(),
                    "请检查 wechat.cli.executable 是否指向可执行文件，以及超时配置是否合理", e);
        }
    }

    /**
     * 把非零退出码翻译成用户能懂的错误。直接照搬源码的退出码语义。
     */
    private void requireSuccess(String subCommand, ProcessRunner.ProcessResult result)
    {
        if (result.success())
        {
            return;
        }
        throw new WechatDataSourceException(describeFailure(subCommand, result));
    }

    private String describeFailure(String subCommand, ProcessRunner.ProcessResult result)
    {
        String stderr = result.stderr() == null ? "" : result.stderr().trim();
        String detail = stderr.isBlank() ? "（无错误输出）" : stderr;

        return switch (result.exitCode())
        {
            case 1 -> "wechat-cli " + subCommand + " 找不到聊天对象或消息记录：" + detail
                    + "。请先用会话列表确认精确的聊天名。";
            case 2 -> "wechat-cli " + subCommand + " 参数无效：" + detail
                    + "。时间格式需为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm[:ss]。";
            case 3 -> "wechat-cli 无法解密 session.db：" + detail
                    + "。请确认微信正在运行，并已在有足够权限的终端执行过 wechat-cli init。";
            default -> "wechat-cli " + subCommand + " 执行失败（退出码 " + result.exitCode() + "）：" + detail;
        };
    }

    private CliExecutableResolver.Resolution resolveExecutable()
    {
        CliExecutableResolver.Resolution cached = this.resolution;
        if (cached != null && cached.found())
        {
            return cached;
        }
        CliExecutableResolver.Resolution res =
                CliExecutableResolver.resolve(properties.getCli().getExecutable());
        this.resolution = res;
        if (res.found())
        {
            log.info("wechat-cli 已定位: {}（来源：{}）", res.path(), res.source());
        }
        else
        {
            log.warn("wechat-cli 未定位: {}", res.failure());
        }
        return res;
    }
}
