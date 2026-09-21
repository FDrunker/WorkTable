package com.ruoyi.wechat.datasource.cli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定位 wechat-cli 可执行文件。
 *
 * <p>为什么需要这个类：按照官方文档 {@code npm install -g @canghe_ai/wechat-cli}
 * 安装后，可执行文件常常<b>不在 PATH 里</b>（本机 {@code .npmrc} 把 prefix 指到了
 * 非默认目录），因此"命令找不到"是常态而非异常。这里按优先级逐个尝试，
 * 并把尝试过程记录下来，方便在 {@code probe()} 里给用户明确的提示。
 */
public class CliExecutableResolver
{
    private static final Logger log = LoggerFactory.getLogger(CliExecutableResolver.class);

    private static final String ENV_KEY = "WECHAT_CLI_BINARY";

    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase().contains("win");

    private CliExecutableResolver()
    {
    }

    /**
     * 解析可执行文件路径。
     *
     * @param configured 配置项 {@code wechat.cli.executable}，可为空
     * @return 解析结果，包含路径与来源说明；找不到时 {@code path()} 为 null
     */
    public static Resolution resolve(String configured)
    {
        List<String> tried = new ArrayList<>();

        // 1. 显式配置优先
        if (configured != null && !configured.isBlank())
        {
            String path = configured.trim();
            tried.add("配置 wechat.cli.executable = " + path);
            if (isExecutable(path))
            {
                return new Resolution(path, "配置项", tried, null);
            }
            // 配置了但不可用：不再继续探测，直接把问题暴露出来
            return new Resolution(null, "配置项", tried,
                    "配置的 wechat-cli 路径不存在或不可执行：" + path);
        }

        // 2. 环境变量
        String env = System.getenv(ENV_KEY);
        if (env != null && !env.isBlank())
        {
            tried.add("环境变量 " + ENV_KEY + " = " + env);
            if (isExecutable(env.trim()))
            {
                return new Resolution(env.trim(), "环境变量", tried, null);
            }
        }

        // 3. PATH 查找
        tried.add("PATH 中查找 " + candidates());
        for (String name : candidates())
        {
            Path found = findOnPath(name);
            if (found != null)
            {
                return new Resolution(found.toString(), "PATH", tried, null);
            }
        }

        // 4. npm 全局目录常见位置
        for (String guess : npmGlobalGuesses())
        {
            tried.add("npm 全局目录猜测 " + guess);
            if (isExecutable(guess))
            {
                return new Resolution(guess, "npm 全局目录", tried, null);
            }
        }

        return new Resolution(null, null, tried, buildNotFoundMessage(tried));
    }

    /**
     * 待尝试的可执行文件名。Windows 下 npm 会生成 .cmd 包装脚本。
     */
    private static List<String> candidates()
    {
        List<String> names = new ArrayList<>();
        names.add("wechat-cli");
        if (WINDOWS)
        {
            names.add("wechat-cli.cmd");
            names.add("wechat-cli.exe");
            names.add("wechat-cli.ps1");
        }
        return names;
    }

    /**
     * 猜测 npm 全局安装位置。这些路径来自 npm prefix 配置与常见约定，
     * 命中不了也没关系，只是少一条自动发现路径。
     */
    private static List<String> npmGlobalGuesses()
    {
        List<String> guesses = new ArrayList<>();
        String userHome = System.getProperty("user.home", "");
        String appData = System.getenv("APPDATA");

        if (appData != null)
        {
            guesses.add(appData + File.separator + "npm" + File.separator + "wechat-cli.cmd");
        }
        if (!userHome.isBlank())
        {
            guesses.add(userHome + File.separator + ".npm-global" + File.separator + "wechat-cli");
            guesses.add(userHome + File.separator + "AppData" + File.separator + "Roaming"
                    + File.separator + "npm" + File.separator + "wechat-cli.cmd");
        }
        // 本机 .npmrc 里配置的 prefix（见 C:\Users\<user>\.npmrc）在 Windows 上
        // 会被拼成 <当前盘>:\home\<user>\.pure-npm-global
        for (String drive : new String[] { "C:", "D:", "E:", "F:" })
        {
            guesses.add(drive + File.separator + "home" + File.separator + "zuijiu"
                    + File.separator + ".pure-npm-global" + File.separator + "wechat-cli.cmd");
        }
        return guesses;
    }

    private static Path findOnPath(String name)
    {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null)
        {
            return null;
        }
        for (String dir : pathEnv.split(File.pathSeparator))
        {
            if (dir.isBlank())
            {
                continue;
            }
            try
            {
                Path candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate))
                {
                    return candidate;
                }
            }
            catch (Exception e)
            {
                // 非法 PATH 条目直接跳过
                log.trace("跳过非法 PATH 条目 {}: {}", dir, e.getMessage());
            }
        }
        return null;
    }

    private static boolean isExecutable(String path)
    {
        if (path == null || path.isBlank())
        {
            return false;
        }
        try
        {
            return Files.isRegularFile(Path.of(path));
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static String buildNotFoundMessage(List<String> tried)
    {
        return "未找到 wechat-cli 可执行文件。已尝试：" + String.join("；", tried)
                + "。请在 application.yml 中显式配置 wechat.cli.executable 为 wechat-cli 的绝对路径。";
    }

    /**
     * 解析结果。
     *
     * @param path    可执行文件路径，未找到时为 null
     * @param source  来源说明（配置项/环境变量/PATH/npm 全局目录）
     * @param tried   已尝试的查找步骤，用于诊断输出
     * @param failure 失败原因，成功时为 null
     */
    public record Resolution(String path, String source, List<String> tried, String failure)
    {
        public boolean found()
        {
            return path != null;
        }
    }
}
