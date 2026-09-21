-- ----------------------------
-- 微信聊天分析模块（恋爱军师）
-- 依赖于 ry_20260417.sql 已执行完毕
-- ----------------------------

-- ----------------------------
-- 1、微信会话表
-- ----------------------------
drop table if exists sys_wechat_session;
create table sys_wechat_session (
  session_id        bigint(20)      not null auto_increment    comment '会话ID',
  user_id           bigint(20)      not null                   comment '归属用户ID（sys_user.user_id）',
  chat_username     varchar(128)    not null                   comment '微信内部标识（wxid_xxx / xxx@chatroom）',
  display_name      varchar(128)    default ''                 comment '展示名（备注优先，其次昵称）',
  is_group          char(1)         default '0'                comment '是否群聊（0否 1是）',
  self_username     varchar(128)    default ''                 comment '「我」的微信标识，由数据源推断',
  self_labels       varchar(500)    default ''                 comment '「我」的原始标签，逗号分隔（用户确认结果）',
  target_labels     varchar(500)    default ''                 comment '对方的原始标签，逗号分隔（用户确认结果）',
  identity_confirmed char(1)        default '0'                comment '说话人映射是否已确认（0未确认 1已确认）',
  msg_count         int(11)         default 0                  comment '已归档消息总数',
  unanalyzed_count  int(11)         default 0                  comment '未分析消息数（冗余字段，加速列表展示）',
  synced_until      varchar(32)     default ''                 comment '已同步到的消息时间（水位线）',
  last_sync_time    datetime                                   comment '最后同步时间',
  del_flag          char(1)         default '0'                comment '删除标志（0存在 2删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (session_id),
  unique key uk_wechat_session_user_chat (user_id, chat_username),
  key idx_wechat_session_user (user_id)
) engine=innodb auto_increment=1 comment = '微信会话表';

-- ----------------------------
-- 2、微信消息归档表
-- ----------------------------
drop table if exists sys_wechat_message;
create table sys_wechat_message (
  message_id        bigint(20)      not null auto_increment    comment '消息ID',
  session_id        bigint(20)      not null                   comment '会话ID',
  user_id           bigint(20)      not null                   comment '归属用户ID',
  msg_time          varchar(32)     default ''                 comment '消息时间文本 yyyy-MM-dd HH:mm',
  msg_date          varchar(10)     default ''                 comment '消息日期 yyyy-MM-dd（便于按天聚合）',
  sender_raw        varchar(128)    default ''                 comment '原始发送者标签（保留证据）',
  sender_role       varchar(16)     default 'UNKNOWN'          comment '归一化角色 SELF/TARGET/OTHER/UNKNOWN',
  content           text                                       comment '清洗后内容',
  content_md5       char(32)        default ''                 comment '内容摘要，用于去重',
  msg_kind          varchar(16)     default 'TEXT'             comment '消息种类 TEXT/QUOTE/MEDIA/VOICE/STICKER/CALL/OTHER（VOICE、STICKER 只展示不参与分析）',
  analyzed          char(1)         default '0'                comment '是否已参与过分析（0未分析 1已分析）',
  analyzed_time     datetime                                   comment '首次参与分析的时间',
  analysis_id       bigint(20)      default null               comment '首次参与的分析ID',
  del_flag          char(1)         default '0'                comment '删除标志（0存在 2删除）',
  create_time       datetime                                   comment '创建时间',
  primary key (message_id),
  unique key uk_wechat_msg_dedup (session_id, msg_time, sender_raw, content_md5),
  key idx_wechat_msg_unanalyzed (session_id, analyzed, message_id),
  key idx_wechat_msg_session_time (session_id, msg_time)
) engine=innodb auto_increment=1 comment = '微信消息归档表';

-- ----------------------------
-- 3、分析记录表
-- ----------------------------
drop table if exists sys_wechat_analysis;
create table sys_wechat_analysis (
  analysis_id       bigint(20)      not null auto_increment    comment '分析ID',
  session_id        bigint(20)      not null                   comment '会话ID',
  user_id           bigint(20)      not null                   comment '归属用户ID',
  chat_name         varchar(128)    default ''                 comment '聊天名快照',
  goal              varchar(1000)   default ''                 comment '用户诉求',
  start_time        varchar(32)     default ''                 comment '本次分析的消息时间范围起',
  end_time          varchar(32)     default ''                 comment '本次分析的消息时间范围止',
  message_id_from   bigint(20)      default null               comment '参与分析的最小消息ID',
  message_id_to     bigint(20)      default null               comment '参与分析的最大消息ID',
  message_count     int(11)         default 0                  comment '参与分析的消息条数',
  identity_desc     varchar(1000)   default ''                 comment '本次使用的说话人映射说明',
  refs_used         varchar(1000)   default ''                 comment '本次加载的知识文档，逗号分隔',
  result_json       longtext                                   comment '军师结构化结果 JSON',
  prompt_snapshot   longtext                                   comment 'prompt 快照，便于复现与排错',
  crisis            char(1)         default '0'                comment '是否命中安全红线（0否 1是）',
  crisis_message    text                                       comment '安全指引内容',
  model             varchar(64)     default ''                 comment '实际使用的模型',
  prompt_tokens     int(11)         default 0                  comment '输入 token 数',
  completion_tokens int(11)         default 0                  comment '输出 token 数',
  duration_ms       bigint(20)      default 0                  comment '耗时毫秒',
  status            char(1)         default '0'                comment '状态（0成功 1失败）',
  error_msg         varchar(2000)   default ''                 comment '失败原因',
  del_flag          char(1)         default '0'                comment '删除标志（0存在 2删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  primary key (analysis_id),
  key idx_wechat_analysis_session (session_id, create_time),
  key idx_wechat_analysis_user (user_id, create_time)
) engine=innodb auto_increment=1 comment = '微信聊天分析记录表';

-- ----------------------------
-- 4、菜单与权限
-- ----------------------------
-- 一级目录：微信分析
insert into sys_menu values('2000', '微信分析', '0', '4', 'wechat', null, '', '', 1, 0, 'M', '0', '0', '', 'message', 'admin', sysdate(), '', null, '微信聊天分析目录');

-- 二级菜单：聊天分析
insert into sys_menu values('2001', '聊天分析', '2000', '1', 'chat', 'wechat/chat/index', '', '', 1, 0, 'C', '0', '0', 'wechat:analysis:list', 'wechat', 'admin', sysdate(), '', null, '微信聊天分析菜单');

-- 按钮权限
insert into sys_menu values('2010', '会话查询', '2001', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'wechat:session:list',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2011', '记录查询', '2001', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'wechat:message:list',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '同步记录', '2001', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'wechat:message:sync',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2013', '执行分析', '2001', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'wechat:analysis:run',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2014', '分析查询', '2001', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'wechat:analysis:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2015', '分析删除', '2001', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'wechat:analysis:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2016', '确认映射', '2001', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'wechat:identity:edit',   '#', 'admin', sysdate(), '', null, '');

-- 分配给超级管理员角色（role_id = 1）
insert into sys_role_menu values('1', '2000');
insert into sys_role_menu values('1', '2001');
insert into sys_role_menu values('1', '2010');
insert into sys_role_menu values('1', '2011');
insert into sys_role_menu values('1', '2012');
insert into sys_role_menu values('1', '2013');
insert into sys_role_menu values('1', '2014');
insert into sys_role_menu values('1', '2015');
insert into sys_role_menu values('1', '2016');
