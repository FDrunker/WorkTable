package com.ruoyi.wechat.datasource.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 外部命令执行器 — 负责超时、编码归一、退出码捕获。
 *
 * <p>刻意不使用 {@code ProcessBuilder.redirectErrorStream} 与默认字符集解码：
 * <ul>
 *   <li>Windows 控制台代码页可能是 GBK，按平台默认字符集读会乱码；</li>
 *   <li>必须先拿字节再显式解码，否则中文聊天记录会变成问号，且不可逆。</li>
 * </ul>
 */
public class ProcessRunner
{
    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    /** 替换字符，用于判断 UTF-8 解码是否失败 */
    private static final char REPLACEMENT = '\uFFFD';

    /**
     * 执行命令并返回结果。
     *
     * @param command    完整命令行（可执行文件 + 参数）
     * @param timeoutSec 超时秒数
     * @param forcedCharset 强制编码，为 null 或空表示自动探测
     */
    public ProcessResult run(List<String> command, int timeoutSec, String forcedCharset) throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        long start = System.currentTimeMillis();
        Process process = pb.start();

        // 必须在 waitFor 之前开始读取，否则管道缓冲区写满会导致子进程阻塞
        StreamCollector stdout = new StreamCollector(process.getInputStream());
        StreamCollector stderr = new StreamCollector(process.getErrorStream());
        Thread outThread = new Thread(stdout, "wechat-cli-stdout");
        Thread errThread = new Thread(stderr, "wechat-cli-stderr");
        outThread.setDaemon(true);
        errThread.setDaemon(true);
        outThread.start();
        errThread.start();

        boolean finished;
        try
        {
            finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("等待 wechat-cli 执行时被中断", e);
        }

        if (!finished)
        {
            process.destroyForcibly();
            throw new IOException("wechat-cli 执行超时（超过 " + timeoutSec + " 秒）。"
                    + "全量历史拉取请调小批次或加大 wechat.cli.timeout-seconds");
        }

        joinQuietly(outThread, errThread);

        byte[] outBytes = stdout.getBytes();
        byte[] errBytes = stderr.getBytes();
        Charset charset = resolveCharset(outBytes, errBytes, forcedCharset);

        ProcessResult result = new ProcessResult(
                process.exitValue(),
                new String(outBytes, charset),
                new String(errBytes, charset),
                System.currentTimeMillis() - start,
                charset.name());

        if (log.isDebugEnabled())
        {
            log.debug("wechat-cli 执行完成: command={}, exit={}, 耗时={}ms, charset={}",
                    command, result.exitCode(), result.durationMs(), result.charset());
        }
        return result;
    }

    /**
     * 自动探测编码：先用 UTF-8 解，出现替换字符则回退 GBK。
     */
    private Charset resolveCharset(byte[] stdout, byte[] stderr, String forcedCharset)
    {
        if (forcedCharset != null && !forcedCharset.isBlank())
        {
            try
            {
                return Charset.forName(forcedCharset.trim());
            }
            catch (Exception e)
            {
                log.warn("wechat.cli.charset 配置无效: {}，回退自动探测", forcedCharset);
            }
        }

        String asUtf8 = new String(stdout, StandardCharsets.UTF_8)
                + new String(stderr, StandardCharsets.UTF_8);
        if (asUtf8.indexOf(REPLACEMENT) >= 0)
        {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }

    private void joinQuietly(Thread... threads)
    {
        for (Thread t : threads)
        {
            try
            {
                t.join(2000);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 命令执行结果。
     *
     * @param exitCode  退出码
     * @param stdout    标准输出（已按探测到的编码解码）
     * @param stderr    标准错误
     * @param durationMs 耗时
     * @param charset   实际使用的编码名
     */
    public record ProcessResult(int exitCode, String stdout, String stderr, long durationMs, String charset)
    {
        public boolean success()
        {
            return exitCode == 0;
        }
    }

    /**
     * 后台读取流的线程体。用 {@code readAllBytes} 语义手动累积，
     * 保证超时强杀时已读到的内容不丢。
     */
    private static class StreamCollector implements Runnable
    {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamCollector(InputStream in)
        {
            this.in = in;
        }

        @Override
        public void run()
        {
            byte[] chunk = new byte[8192];
            try
            {
                int n;
                while ((n = in.read(chunk)) != -1)
                {
                    buffer.write(chunk, 0, n);
                }
            }
            catch (IOException e)
            {
                // 进程被强杀时流会异常关闭，已读内容仍然保留
                log.debug("读取 wechat-cli 输出流中断: {}", e.getMessage());
            }
        }

        byte[] getBytes()
        {
            return buffer.toByteArray();
        }
    }

    /**
     * 便捷构造：把可执行文件与参数拼成命令列表。
     */
    public static List<String> command(String executable, String... args)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        cmd.addAll(Arrays.asList(args));
        return cmd;
    }
}
