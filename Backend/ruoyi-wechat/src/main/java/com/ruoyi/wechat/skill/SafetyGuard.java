package com.ruoyi.wechat.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 安全兜底 —— 危机情境检测。
 *
 * <p>为什么不能只靠 prompt：goutoujunshi 的 SKILL.md 里明确要求
 * "出现家暴、跟踪、强迫、财务控制、人身威胁或立即自伤、伤人、生命危险时，
 * 先确认当下安全并联系可信支持或当地紧急服务"。把这条<b>只</b>写在 prompt 里，
 * 就等于把合规红线交给模型自由裁量。这里做关键词兜底，命中后走独立分支。
 *
 * <p>策略是"宁可误报也不漏报"：误报的代价只是多一次安全提示，
 * 漏报的代价可能很严重。
 */
@Component
public class SafetyGuard
{
    /**
     * 危机关键词。分成若干类只是为了让命中的提示更具体。
     */
    private static final List<Category> CATEGORIES = List.of(
            new Category("人身安全威胁", Pattern.compile(
                    "家暴|家庭暴力|打我|打了我|动手打|殴打|掐我|扇我|踹我|拿刀|持刀|威胁我|扬言|报复我|"
                            + "杀了我|弄死|同归于尽|伤害我|人身威胁")),
            new Category("跟踪与骚扰", Pattern.compile(
                    "跟踪|尾随|蹲守|堵门|堵我|骚扰我|一直打电话|疯狂打电话|监视我|定位我|偷看我手机")),
            new Category("强迫与控制", Pattern.compile(
                    "强迫我|逼我|不让我|控制我|限制我|没收我|软禁|囚禁|财务控制|拿我的钱|不给我钱")),
            new Category("自伤风险", Pattern.compile(
                    "自杀|自残|割腕|不想活|活不下去|想死|结束生命|轻生|跳楼")),
            new Category("诈骗与勒索", Pattern.compile(
                    "勒索|敲诈|诈骗|被骗了钱|裸照威胁|不雅照威胁|要挟我")),
            new Category("未同意私密影像", Pattern.compile(
                    "偷拍|偷录|私自拍|传播我的照片|散布隐私|发我照片给别人")));

    /**
     * 检测文本中的危机信号。
     *
     * @param text 待检测文本（聊天记录 + 用户诉求）
     * @return 命中的类别列表，未命中时为空
     */
    public List<String> detect(String text)
    {
        List<String> hits = new ArrayList<>();
        if (text == null || text.isBlank())
        {
            return hits;
        }
        for (Category c : CATEGORIES)
        {
            if (c.pattern.matcher(text).find())
            {
                hits.add(c.name);
            }
        }
        return hits;
    }

    /**
     * 命中危机时给用户的固定指引。这段内容对应 SKILL.md 的安全边界，
     * 不交给模型生成，避免被"话术化"削弱。
     */
    public String crisisMessage(List<String> hits)
    {
        return "检测到可能涉及【" + String.join("、", hits) + "】的内容。\n\n"
                + "这类情况不适用普通的恋爱沟通建议。请优先处理安全问题：\n\n"
                + "1. 如果此刻存在人身危险，请立即离开现场并联系当地紧急服务（中国大陆：110；医疗急救：120）。\n"
                + "2. 保留证据：聊天记录、通话记录、伤情照片、转账凭证，不要在情绪激动时删除对话。\n"
                + "3. 联系可信赖的家人、朋友或专业机构，不要独自应对。\n"
                + "4. 中国大陆可拨打全国妇联维权热线 12338 获取法律援助与庇护转介。\n\n"
                + "如果你现在处于情绪危机中，请联系专业心理援助热线（如全国心理援助热线 12356）。\n\n"
                + "本工具只提供关系教育参考，不能替代心理治疗、医疗诊断、律师意见、警方或紧急服务。";
    }

    private record Category(String name, Pattern pattern)
    {
    }
}
