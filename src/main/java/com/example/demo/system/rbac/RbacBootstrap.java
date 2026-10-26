package com.example.demo.system.rbac;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 只初始化已有账号；不创建默认密码，不把首个注册者自动提权。 */
@Component
public class RbacBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final String adminAccount;
    public RbacBootstrap(JdbcTemplate jdbc,@Value("${rbac.bootstrap-admin-account:}") String adminAccount) {
        this.jdbc=jdbc; this.adminAccount=adminAccount;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        Boolean userTable=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection->{
            try(var tables=connection.getMetaData().getTables(connection.getCatalog(),null,"user",new String[]{"TABLE"})) { return tables.next(); }
        });
        if(!Boolean.TRUE.equals(userTable)) return;
        jdbc.queryForObject("SELECT id FROM sys_role WHERE code='super_admin' FOR UPDATE",Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,2 FROM `user` u WHERE NOT EXISTS(SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.id)");
        if(adminAccount.isBlank()) return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN `user` u ON u.id=ur.user_id WHERE ur.role_id=1",Long.class)>0) return;
        var ids=jdbc.queryForList("SELECT id FROM `user` WHERE account=?",Integer.class,adminAccount.trim());
        if(ids.size()!=1) throw new IllegalStateException("RBAC 初始化管理员账号必须唯一且已存在，请检查 rbac.bootstrap-admin-account");
        jdbc.update("UPDATE sys_user_role SET role_id=1 WHERE user_id=?",ids.getFirst());
    }
}
