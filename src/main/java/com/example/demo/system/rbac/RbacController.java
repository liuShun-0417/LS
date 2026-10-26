package com.example.demo.system.rbac;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.system.rbac.RbacModels.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RbacController {
    private final RbacService service;
    public RbacController(RbacService service) { this.service=service; }
    @GetMapping("/auth/access") public Access access() { return service.access(StpUtil.getLoginIdAsInt()); }
    @GetMapping("/roles") public List<Role> roles() { StpUtil.checkPermission("role:read"); return service.roles(); }
    @GetMapping("/roles/options") public List<Role> options() {
        StpUtil.checkPermission("user:assign-role");
        return service.roles().stream().filter(Role::isEnabled).toList();
    }
    @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED)
    public Role createRole(@Valid @RequestBody RoleRequest request) { StpUtil.checkPermission("role:create"); return service.saveRole(null,request); }
    @PutMapping("/roles/{id}")
    public Role updateRole(@PathVariable long id,@Valid @RequestBody RoleRequest request) { StpUtil.checkPermission("role:update"); return service.saveRole(id,request); }
    @DeleteMapping("/roles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable long id) { StpUtil.checkPermission("role:delete"); service.deleteRole(id); }
    @GetMapping("/roles/{id}/permissions") public List<Long> grants(@PathVariable long id) { StpUtil.checkPermission("role:grant"); return service.grants(id); }
    @PutMapping("/roles/{id}/permissions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(@PathVariable long id,@Valid @RequestBody GrantRequest request) { StpUtil.checkPermission("role:grant"); service.grant(id,request.menuIds()); }
    @GetMapping("/menus") public List<Menu> menus() { StpUtil.checkPermission("menu:read"); return service.menus(); }
    @GetMapping("/menus/permission-tree") public List<Menu> permissionTree() { StpUtil.checkPermission("role:grant"); return service.menus(); }
    @PostMapping("/menus") @ResponseStatus(HttpStatus.CREATED)
    public Menu createMenu(@Valid @RequestBody MenuRequest request) {
        StpUtil.checkPermission("menu:create");
        return service.saveMenu(null,request); }
    @PutMapping("/menus/{id}")
    public Menu updateMenu(@PathVariable long id,@Valid @RequestBody MenuRequest request) { StpUtil.checkPermission("menu:update"); return service.saveMenu(id,request); }
    @DeleteMapping("/menus/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenu(@PathVariable long id) { StpUtil.checkPermission("menu:delete"); service.deleteMenu(id); }
    @PutMapping("/users/{id}/role") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bind(@PathVariable int id,@Valid @RequestBody BindRequest request) { StpUtil.checkPermission("user:assign-role"); service.bind(id,request.roleId()); }
}
