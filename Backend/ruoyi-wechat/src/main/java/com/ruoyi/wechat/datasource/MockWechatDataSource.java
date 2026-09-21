package com.ruoyi.wechat.datasource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置样例数据源 — 用于联调、演示与自动化测试。
 *
 * <p>刻意构造得"脏"一些，以便验证清洗链路的每条规则确实生效：
 * <ul>
 *   <li>含系统消息、表情、图片、通话等应当被降噪的内容</li>
 *   <li>含连续同一人发言，用于验证折叠</li>
 *   <li>含手机号，用于验证脱敏</li>
 *   <li>含正文里的冒号，用于验证行解析不会误切</li>
 * </ul>
 */
public class MockWechatDataSource implements WechatDataSource
{
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String CHAT = "小美";

    /** 样例会话的另一方原始标签，模拟数据源给出的备注名 */
    private static final String TARGET = "小美";

    /** 模拟"我"的原始标签。CLI 数据源对自己会给出字面量 me */
    private static final String SELF = "me";

    @Override
    public String mode()
    {
        return "mock";
    }

    @Override
    public SourceStatus probe()
    {
        return SourceStatus.ok(mode(), "使用内置样例数据，无需任何外部依赖（用于联调演示）");
    }

    @Override
    public List<ChatSession> listSessions(int limit)
    {
        List<ChatSession> sessions = new ArrayList<>();
        sessions.add(new ChatSession("wxid_mock_xiaomei", CHAT, false, 2,
                "那周六见咯", "04-03 21:40", SELF));
        sessions.add(new ChatSession("12345678@chatroom", "周末爬山群", true, 15,
                "[图片]", "04-03 20:12", SELF));
        sessions.add(new ChatSession("wxid_mock_laowang", "老王", false, 0,
                "收到", "04-01 09:05", SELF));
        return sessions.size() > limit ? sessions.subList(0, limit) : sessions;
    }

    @Override
    public List<RawMessage> fetchMessages(FetchQuery query)
    {
        LocalDateTime base = LocalDateTime.of(2026, 4, 1, 19, 0);
        List<RawMessage> list = new ArrayList<>();
        int seq = 0;

        seq = add(list, seq, base.plusMinutes(0), null, "你已添加了" + TARGET + "，现在可以开始聊天了");
        seq = add(list, seq, base.plusMinutes(2), SELF, "在吗？今天那个方案我看完了");
        seq = add(list, seq, base.plusMinutes(6), TARGET, "在的，你觉得哪里有问题？");
        seq = add(list, seq, base.plusMinutes(9), SELF, "倒也不是问题，就是第三节的时间点：我算了一下，比预期晚两周");
        seq = add(list, seq, base.plusMinutes(15), TARGET, "[表情]");
        seq = add(list, seq, base.plusMinutes(16), TARGET, "我想想");
        seq = add(list, seq, base.plusMinutes(17), TARGET, "嗯");
        seq = add(list, seq, base.plusMinutes(18), TARGET, "确实");
        seq = add(list, seq, base.plusMinutes(19), TARGET, "那要不挪到下周？");
        seq = add(list, seq, base.plusMinutes(20), TARGET, "你看看行不行");

        // 跨天：制造"我主动、对方回应变慢"的行为模式，供军师分析互惠
        LocalDateTime day2 = LocalDateTime.of(2026, 4, 2, 10, 30);
        seq = add(list, seq, day2, SELF, "早，昨天说的时间挪到下周三可以吗");
        seq = add(list, seq, day2.plusHours(6), TARGET, "刚看到，可以");
        seq = add(list, seq, day2.plusHours(6).plusMinutes(2), TARGET, "手机 13812345678 是我新的，工作的事打这个");
        seq = add(list, seq, day2.plusHours(7), SELF, "[图片]");
        seq = add(list, seq, day2.plusHours(7).plusMinutes(3), SELF, "这是改完的甘特图");
        seq = add(list, seq, day2.plusHours(8), TARGET, "收到啦，辛苦");
        seq = add(list, seq, day2.plusHours(9), null, "对方已取消通话");

        LocalDateTime day3 = LocalDateTime.of(2026, 4, 3, 21, 30);
        seq = add(list, seq, day3, SELF, "周末有空吗，想请你吃饭，上次那个项目多亏你");
        seq = add(list, seq, day3.plusMinutes(10), TARGET, "哈哈好啊，那周六见咯");

        return list;
    }

    private int add(List<RawMessage> list, int seq, LocalDateTime time, String sender, String content)
    {
        list.add(new RawMessage(seq, time.format(FMT), sender, content));
        return seq + 1;
    }
}
