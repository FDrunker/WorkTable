package com.ruoyi.wechat.skill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import com.ruoyi.wechat.config.WechatProperties;

import jakarta.annotation.PostConstruct;

/**
 * 从 classpath 加载军师 skill 资源。
 *
 * <p>资源布局（对应 goutoujunshi 仓库结构）：
 * <pre>
 * src/main/resources/skill/
 * ├── SKILL.md                 # 行为与路由内核（必需）
 * ├── routing.md               # 路由表（可选，缺失时退回 SKILL.md 内的路由表）
 * └── references/
 *     ├── knowledge/*.md
 *     └── practical/*.md
 * </pre>
 *
 * <p>刻意采用<b>懒加载 + 容错</b>：缺少 SKILL.md 只记录警告，不让应用启动失败。
 * 这样在只跑通数据链路的早期阶段，模块依然可用。
 */
@Component
public class SkillResourceLoader
{
    private static final Logger log = LoggerFactory.getLogger(SkillResourceLoader.class);

    private static final String SKILL_MD = "SKILL.md";

    private static final String ROUTING_MD = "routing.md";

    private final WechatProperties properties;

    /** path → 文档，进程内缓存 */
    private final Map<String, SkillReference> referenceCache = new ConcurrentHashMap<>();

    private volatile String skillMd;

    private volatile String routingTable;

    private volatile boolean loaded;

    public SkillResourceLoader(WechatProperties properties)
    {
        this.properties = properties;
    }

    @PostConstruct
    public void init()
    {
        try
        {
            load();
        }
        catch (Exception e)
        {
            // 资源缺失不是致命错误，只有真正要分析时才需要它
            log.warn("加载军师 skill 资源失败：{}。分析功能将不可用，数据同步等功能不受影响。", e.getMessage());
        }
    }

    /**
     * 加载全部资源到内存。总大小约 250KB，常驻内存代价可接受。
     */
    public synchronized void load()
    {
        String base = normalizeBase(properties.getSkill().getLocation());
        this.skillMd = readOptional(base + SKILL_MD);
        this.routingTable = readOptional(base + ROUTING_MD);

        if (skillMd == null)
        {
            log.warn("未找到 {}。请把 goutoujunshi 仓库的 SKILL.md 与 references/ 放入 "
                    + "ruoyi-wechat/src/main/resources/skill/ 目录。当前路径：{}", SKILL_MD, base);
        }
        else if (routingTable == null)
        {
            // routing.md 是 SKILL.md 内路由表的抽取版；缺失时不影响 SKILL.md 本身可用
            log.info("未找到 {}，将直接使用 SKILL.md 全文作为行为内核", ROUTING_MD);
        }

        referenceCache.clear();
        List<Resource> resources = findReferences(base);
        for (Resource r : resources)
        {
            try
            {
                String path = toRelativePath(base, r);
                String content = read(r);
                referenceCache.put(path, new SkillReference(path, displayName(path), content,
                        DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8))));
            }
            catch (Exception e)
            {
                log.warn("读取参考文档失败 {}: {}", r.getFilename(), e.getMessage());
            }
        }
        this.loaded = true;
        log.info("军师 skill 资源加载完成：SKILL.md={}，参考文档={} 篇，路由表={}",
                skillMd != null, referenceCache.size(), routingTable != null);
    }

    public boolean isReady()
    {
        return skillMd != null;
    }

    public String getSkillMd()
    {
        return skillMd;
    }

    public String getRoutingTable()
    {
        return routingTable;
    }

    public List<SkillReference> allReferences()
    {
        return new ArrayList<>(referenceCache.values());
    }

    /**
     * 按路径取参考文档，找不到时返回 null（用于过滤模型给出的非法路径）。
     */
    public SkillReference getReference(String path)
    {
        if (path == null)
        {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        SkillReference direct = referenceCache.get(normalized);
        if (direct != null)
        {
            return direct;
        }
        // 容忍模型只给出文件名而省略目录
        for (Map.Entry<String, SkillReference> e : referenceCache.entrySet())
        {
            if (e.getKey().endsWith("/" + normalized) || e.getValue().displayName().equals(normalized))
            {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 提供"文件名 → 主题关键词"的索引，供路由阶段作为白名单与提示使用。
     */
    public Map<String, String> describeCatalog()
    {
        Map<String, String> catalog = new LinkedHashMap<>();
        for (Map.Entry<String, SkillReference> e : referenceCache.entrySet())
        {
            catalog.put(e.getKey(), e.getValue().displayName());
        }
        return catalog;
    }

    private List<Resource> findReferences(String base)
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> all = new ArrayList<>();
        for (String pattern : new String[] { base + "references/**/*.md", base + "references/*.md" })
        {
            try
            {
                Resource[] found = resolver.getResources(pattern);
                for (Resource r : found)
                {
                    if (r.isReadable() && !all.contains(r))
                    {
                        all.add(r);
                    }
                }
            }
            catch (IOException e)
            {
                log.debug("匹配 skill 资源失败 pattern={}: {}", pattern, e.getMessage());
            }
        }
        return all;
    }

    private String toRelativePath(String base, Resource resource) throws IOException
    {
        String uri = resource.getURI().toString().replace('\\', '/');
        int idx = uri.indexOf("/references/");
        if (idx >= 0)
        {
            return uri.substring(idx + 1);
        }
        String filename = resource.getFilename();
        return "references/" + (filename == null ? "unknown.md" : filename);
    }

    private String displayName(String path)
    {
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0)
        {
            name = name.substring(slash + 1);
        }
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private String readOptional(String location)
    {
        try
        {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(location);
            if (resource.isReadable())
            {
                return read(resource);
            }
        }
        catch (Exception e)
        {
            log.debug("读取 {} 失败: {}", location, e.getMessage());
        }
        return null;
    }

    private String read(Resource resource) throws IOException
    {
        try (InputStream in = resource.getInputStream())
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalizeBase(String location)
    {
        String base = location == null || location.isBlank() ? "classpath:skill/" : location.trim();
        if (!base.endsWith("/"))
        {
            base = base + "/";
        }
        return base;
    }

    /**
     * 供测试或热更新使用。
     */
    public boolean isLoaded()
    {
        return loaded;
    }
}
