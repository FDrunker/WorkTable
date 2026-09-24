-- ----------------------------
-- 调整知识库、模型管理菜单层级，并删除若依官网菜单
-- 适用于已经执行过 ai-knowledge.sql 和 AI对话迁移脚本的数据库
-- ----------------------------

-- 知识库和模型管理提升为一级菜单，原知识库目录不再使用
delete from sys_role_menu where menu_id = '3000';
delete from sys_menu where menu_id = '3000';

update sys_menu
set menu_name = '知识库管理',
    parent_id = '0',
    order_num = '5',
    path = 'knowledge-base',
    component = 'knowledge/collection/index',
    perms = 'knowledge:collection:list',
    icon = 'collection',
    remark = '知识库管理菜单'
where menu_id = '3001';

update sys_menu
set parent_id = '0',
    order_num = '6',
    path = 'model',
    component = 'knowledge/model/index',
    perms = 'knowledge:model:list',
    icon = 'cpu',
    remark = 'AI 模型配置菜单'
where menu_id = '3002';

update sys_menu set menu_name = '知识库查询' where menu_id = '3010';
update sys_menu set menu_name = '新增知识库' where menu_id = '3019';
update sys_menu set menu_name = '删除知识库' where menu_id = '3020';
update sys_menu set order_num = '7' where menu_id = '3003';

-- 删除若依官网菜单及角色关联
delete from sys_role_menu where menu_id = '4';
delete from sys_menu where menu_id = '4';
