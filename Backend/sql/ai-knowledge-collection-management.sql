-- ----------------------------
-- 知识库集合新增、删除权限迁移
-- 适用于已经执行过 ai-knowledge.sql 的数据库。
-- ----------------------------

delete from sys_role_menu where menu_id in ('3019', '3020');
delete from sys_menu where menu_id in ('3019', '3020');

insert into sys_menu values('3019', '新增集合', '3001', '10', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3020', '删除集合', '3001', '11', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:remove', '#', 'admin', sysdate(), '', null, '');

insert into sys_role_menu values('1', '3019');
insert into sys_role_menu values('1', '3020');
