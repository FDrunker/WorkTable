-- ----------------------------
-- 微信会话删除权限迁移
-- 适用于已经执行过 wechat.sql 的数据库；全新安装直接执行最新的 wechat.sql。
-- ----------------------------

insert into sys_menu values('2017', '删除会话', '2001', '8', '', '', '', '', 1, 0, 'F', '0', '0',
  'wechat:session:remove', '#', 'admin', sysdate(), '', null, '');

insert into sys_role_menu values('1', '2017');
