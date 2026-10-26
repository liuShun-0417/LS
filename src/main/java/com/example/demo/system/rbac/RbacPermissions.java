package com.example.demo.system.rbac;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RbacPermissions implements StpInterface {
    private final RbacService service;
    public RbacPermissions(RbacService service) { this.service=service; }
    @Override public List<String> getPermissionList(Object loginId,String loginType) {
        return service.access(Integer.parseInt(loginId.toString())).permissions();
    }
    @Override public List<String> getRoleList(Object loginId,String loginType) {
        var role=service.access(Integer.parseInt(loginId.toString())).role();
        return role == null || !role.isEnabled() ? List.of() : List.of(role.getCode());
    }
}
