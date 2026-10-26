CREATE TABLE IF NOT EXISTS sys_role (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
 code VARCHAR(64) NOT NULL UNIQUE, description VARCHAR(255),
 enabled BOOLEAN NOT NULL DEFAULT TRUE, builtin BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS sys_menu (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, parent_id BIGINT,
 name VARCHAR(100) NOT NULL, type VARCHAR(16) NOT NULL,
 path VARCHAR(200), permission VARCHAR(100) UNIQUE,
 sort_order INT NOT NULL DEFAULT 0, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 builtin BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_menu_parent FOREIGN KEY(parent_id) REFERENCES sys_menu(id)
);
CREATE TABLE IF NOT EXISTS sys_role_menu (
 role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, PRIMARY KEY(role_id,menu_id),
 CONSTRAINT fk_grant_role FOREIGN KEY(role_id) REFERENCES sys_role(id),
 CONSTRAINT fk_grant_menu FOREIGN KEY(menu_id) REFERENCES sys_menu(id)
);
CREATE TABLE IF NOT EXISTS sys_user_role (
 user_id INT PRIMARY KEY, role_id BIGINT NOT NULL,
 CONSTRAINT fk_binding_role FOREIGN KEY(role_id) REFERENCES sys_role(id)
);
INSERT INTO sys_role(id,name,code,description,enabled,builtin)
SELECT 1,'超级管理员','super_admin','拥有全部系统权限',TRUE,TRUE
WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE id=1);
INSERT INTO sys_role(id,name,code,description,enabled,builtin)
SELECT 2,'普通用户','member','默认角色：消息演示与群组聊天',TRUE,TRUE
WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE id=2);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 1,NULL,'系统管理','DIRECTORY',NULL,NULL,10,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 10,1,'用户管理','MENU','/users.html','user:read',10,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=10);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 11,10,'新增用户','BUTTON',NULL,'user:create',10,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=11);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 12,10,'编辑用户','BUTTON',NULL,'user:update',20,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=12);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 13,10,'删除用户','BUTTON',NULL,'user:delete',30,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=13);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 14,10,'绑定角色','BUTTON',NULL,'user:assign-role',40,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=14);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 20,1,'角色管理','MENU','/roles.html','role:read',20,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=20);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 21,20,'新增角色','BUTTON',NULL,'role:create',10,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=21);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 22,20,'编辑角色','BUTTON',NULL,'role:update',20,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=22);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 23,20,'删除角色','BUTTON',NULL,'role:delete',30,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=23);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 24,20,'分配权限','BUTTON',NULL,'role:grant',40,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=24);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 30,1,'菜单管理','MENU','/menus.html','menu:read',30,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=30);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 31,30,'新增菜单','BUTTON',NULL,'menu:create',10,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=31);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 32,30,'编辑菜单','BUTTON',NULL,'menu:update',20,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=32);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 33,30,'删除菜单','BUTTON',NULL,'menu:delete',30,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=33);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 40,NULL,'群组聊天','MENU','/chat.html','chat:access',20,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=40);
INSERT INTO sys_menu(id,parent_id,name,type,path,permission,sort_order,enabled,builtin)
SELECT 50,NULL,'消息演示','MENU','/','message:access',30,TRUE,TRUE WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=50);
CREATE TABLE IF NOT EXISTS sys_rbac_migration (version INT PRIMARY KEY);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT 2,40 WHERE NOT EXISTS(SELECT 1 FROM sys_rbac_migration WHERE version=1) AND NOT EXISTS(SELECT 1 FROM sys_role_menu WHERE role_id=2 AND menu_id=40);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT 2,50 WHERE NOT EXISTS(SELECT 1 FROM sys_rbac_migration WHERE version=1) AND NOT EXISTS(SELECT 1 FROM sys_role_menu WHERE role_id=2 AND menu_id=50);
INSERT INTO sys_rbac_migration(version) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM sys_rbac_migration WHERE version=1);

