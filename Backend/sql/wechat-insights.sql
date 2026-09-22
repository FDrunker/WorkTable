-- ----------------------------
-- 微信聊天界面关系洞察字段迁移
-- 适用于已经执行过 wechat.sql 的现有数据库；全新安装直接执行最新的 wechat.sql 即可。
-- 本脚本只执行一次。
-- ----------------------------

alter table sys_wechat_session
  add column affinity_score decimal(5,2) default null comment '当前好感度百分比，空表示尚未计算' after last_sync_time,
  add column acquaintance_date varchar(10) default null comment '认识日期 yyyy-MM-dd，由用户填写' after affinity_score,
  add column my_performance_score decimal(5,2) default null comment '我的回答综合发挥得分' after acquaintance_date,
  add column next_step_suggestion text comment '下一步建议，预留给大模型输出' after my_performance_score;

alter table sys_wechat_message
  add column emotion_tag varchar(64) default null comment '对方消息情绪标签' after analysis_id,
  add column emotion_percent decimal(5,2) default null comment '情绪标签百分比 0-100' after emotion_tag,
  add column intent_tag varchar(64) default null comment '对方消息意图标签' after emotion_percent,
  add column intent_percent decimal(5,2) default null comment '意图标签百分比 0-100' after intent_tag,
  add column reply_rating varchar(16) default null comment '我方回答评级' after intent_percent,
  add column reply_score decimal(5,2) default null comment '我方回答得分，用于综合发挥' after reply_rating;
