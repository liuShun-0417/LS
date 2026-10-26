package com.example.demo.system.rbac;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.system.mapper.UserMapper;
import com.example.demo.system.rbac.RbacModels.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class RbacService {
    private final RbacMapper mapper;
    private final UserMapper users;
    public RbacService(RbacMapper mapper, UserMapper users) { this.mapper=mapper; this.users=users; }

    public List<Role> roles() { return mapper.roles(); }
    public List<Menu> menus() { return mapper.menus(); }
    public Role role(long id) {
        Role role=mapper.role(id);
        if(role==null) throw error(404,"角色不存在");
        return role;
    }
    public Role userRole(int userId) {
        // 兼容迁移前的用户；新建用户均在同一事务中写入唯一角色绑定。
        return mapper.effectiveRole(userId);
    }
    public List<com.example.demo.system.dto.UserResponse> userResponses(List<Integer> ids) {
        if(ids.isEmpty()) return List.of();
        var byId=new HashMap<Integer,com.example.demo.system.dto.UserResponse>();
        mapper.userResponses(ids).forEach(user->byId.put(user.id(),user));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
    public Access access(int userId) {
        // 仅在同一 HTTP 请求内复用，避免权限树逐项校验反复查询；跨请求不缓存，撤权立即生效。
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String key="rbac.access."+userId;
        if(attributes!=null && attributes.getAttribute(key,0) instanceof Access cached) return cached;
        Access result=loadAccess(userId);
        if(attributes!=null) attributes.setAttribute(key,result,0);
        return result;
    }
    private Access loadAccess(int userId) {
        Role role=userRole(userId);
        if(role==null) throw error(401,"登录已失效");
        if(!role.isEnabled()) return new Access(role,List.of(),List.of());
        List<Menu> all=menus();
        boolean admin="super_admin".equals(role.getCode());
        Set<Long> selected=new HashSet<>(admin ? all.stream().map(Menu::getId).toList() : mapper.grants(role.getId()));
        Map<Long,Menu> byId=new HashMap<>(); all.forEach(m->byId.put(m.getId(),m));
        List<Menu> available=all.stream().filter(m->selected.contains(m.getId()) && active(m,byId)).toList();
        List<String> permissions=admin ? List.of("*") : available.stream().map(Menu::getPermission).filter(Objects::nonNull).distinct().toList();
        Set<Long> visible=new HashSet<>();
        for(Menu menu:available) {
            if("BUTTON".equals(menu.getType())) continue;
            Menu current=menu;
            while(current!=null && visible.add(current.getId())) current=byId.get(current.getParentId());
        }
        return new Access(role,permissions,all.stream().filter(m->visible.contains(m.getId())).toList());
    }
    private boolean active(Menu menu,Map<Long,Menu> all) {
        Set<Long> visited=new HashSet<>();
        while(menu!=null) {
            if(!menu.isEnabled() || !visited.add(menu.getId())) return false;
            if(menu.getParentId()==null) return true;
            menu=all.get(menu.getParentId());
        }
        return false;
    }
    public void check(int userId,String permission) {
        List<String> permissions=access(userId).permissions();
        if(!permissions.contains("*") && !permissions.contains(permission)) throw error(403,"没有操作权限："+permission);
    }
    public void protectAdministrator(int userId) {
        mapper.lock();
        Role target=mapper.userRole(userId);
        if(target!=null && "super_admin".equals(target.getCode()) && !StpUtil.hasRole("super_admin"))
            throw error(403,"只有超级管理员可以维护超级管理员账号");
    }
    private void checkGrantScope(Collection<Long> ids) {
        if(StpUtil.hasRole("super_admin")) return;
        for(Long id:ids) {
            Menu menu=mapper.menu(id);
            if(menu!=null && menu.getPermission()!=null && !StpUtil.hasPermission(menu.getPermission()))
                throw error(403,"不能授予或管理超出自身权限的角色");
        }
    }
    public List<Long> grants(long roleId) {
        return "super_admin".equals(role(roleId).getCode()) ? menus().stream().map(Menu::getId).toList() : mapper.grants(roleId);
    }

    @Transactional
    public Role saveRole(Long id,RoleRequest request) {
        mapper.lock();
        Role role=id==null ? new Role() : role(id);
        if(id!=null) { if("super_admin".equals(role.getCode()) && !StpUtil.hasRole("super_admin")) throw error(403,"只有超级管理员可以维护该角色"); checkGrantScope(mapper.grants(id)); }
        if(role.isBuiltin() && (!role.getCode().equals(request.code()) || !request.enabled()))
            throw error(400,"内置角色不能修改编码或停用");
        Role existing=mapper.roleByCode(request.code());
        if(existing!=null && !existing.getId().equals(id)) throw error(409,"角色编码已存在");
        role.setName(request.name().trim()); role.setCode(request.code());
        role.setDescription(request.description()); role.setEnabled(request.enabled());
        if(id==null) mapper.insertRole(role); else mapper.updateRole(role);
        return role(role.getId());
    }
    @Transactional
    public void deleteRole(long id) {
        mapper.lock();
        if(role(id).isBuiltin()) throw error(400,"内置角色不能删除");
        checkGrantScope(mapper.grants(id));
        if(mapper.userCount(id)>0) throw error(409,"该角色已绑定用户，请先更换用户角色");
        mapper.clearGrants(id); mapper.deleteRole(id);
    }
    @Transactional
    public void grant(long roleId,List<Long> menuIds) {
        mapper.lock();
        if("super_admin".equals(role(roleId).getCode())) throw error(400,"超级管理员始终拥有全部权限");
        Set<Long> ids=new LinkedHashSet<>(menuIds);
        Set<Long> existing=new HashSet<>(menus().stream().map(Menu::getId).toList());
        if(!existing.containsAll(ids)) throw error(400,"选择的菜单或权限不存在");
        checkGrantScope(mapper.grants(roleId));
        // 自动补全祖先，保证页面权限与按钮权限能连贯使用。
        for(Long id:List.copyOf(ids)) {
            Menu menu=mapper.menu(id);
            while(menu.getParentId()!=null) { ids.add(menu.getParentId()); menu=mapper.menu(menu.getParentId()); }
        }
        checkGrantScope(ids);
        mapper.clearGrants(roleId);
        ids.forEach(id->mapper.grant(roleId,id));
    }
    @Transactional
    public void bind(int userId,Long roleId) {
        mapper.lock();
        if(users.selectById(userId)==null) throw error(404,"用户不存在");
        Role target=roleId==null ? mapper.roleByCode("member") : role(roleId);
        if(!target.isEnabled()) throw error(400,"不能绑定已停用的角色");
        Role old=mapper.userRole(userId);
        if(StpUtil.isLogin()) {
            protectAdministrator(userId);
            if("super_admin".equals(target.getCode()) && !StpUtil.hasRole("super_admin")) throw error(403,"只有超级管理员可以授予超级管理员角色");
            if(roleId!=null) checkGrantScope(mapper.grants(target.getId()));
        }
        if(old!=null && !old.getId().equals(target.getId())) protectLastAdmin(userId);
        mapper.unbind(userId); mapper.bind(userId,target.getId());
    }
    public void protectLastAdmin(int userId) {
        mapper.lock();
        Role old=mapper.userRole(userId);
        if(old!=null && "super_admin".equals(old.getCode()) && mapper.userCount(old.getId())<=1)
            throw error(409,"至少保留一个超级管理员");
    }
    public void removeBinding(int userId) { mapper.unbind(userId); }

    @Transactional
    public Menu saveMenu(Long id,MenuRequest request) {
        mapper.lock();
        Menu menu=id==null ? new Menu() : mapper.menu(id);
        if(menu==null) throw error(404,"菜单不存在");
        String path=blankToNull(request.path()), permission=blankToNull(request.permission());
        if("MENU".equals(request.type()) && (path==null || !path.matches("/|/(?:[a-zA-Z0-9_-]+/)*[a-zA-Z0-9_-]+\\.html")))
            throw error(400,"页面地址须为本站 / 或 /xxx.html 路径");
        if(!"MENU".equals(request.type()) && path!=null) throw error(400,"目录和按钮不能设置页面地址");
        if(!"DIRECTORY".equals(request.type()) && (permission==null || !permission.matches("[a-zA-Z][a-zA-Z0-9_-]*:[a-zA-Z][a-zA-Z0-9_-]*")))
            throw error(400,"页面和按钮需填写权限标识，如 user:read");
        if("DIRECTORY".equals(request.type()) && permission!=null) throw error(400,"目录不设置权限标识");
        if(menu.isBuiltin() && (!Objects.equals(menu.getParentId(),request.parentId()) || !menu.getType().equals(request.type())
                || !Objects.equals(menu.getPath(),path) || !Objects.equals(menu.getPermission(),permission) || !request.enabled()))
            throw error(400,"内置菜单仅允许修改名称和排序");
        if(request.parentId()!=null) {
            Menu parent=mapper.menu(request.parentId());
            if(parent==null) throw error(400,"上级菜单不存在");
            if("BUTTON".equals(parent.getType()) || ("BUTTON".equals(request.type()) && !"MENU".equals(parent.getType()))
                    || (!"BUTTON".equals(request.type()) && !"DIRECTORY".equals(parent.getType()))) throw error(400,"目录下放目录或页面，页面下放按钮权限");
            while(parent!=null) {
                if(parent.getId().equals(id)) throw error(400,"不能将菜单移到自身或后代下面");
                parent=parent.getParentId()==null ? null : mapper.menu(parent.getParentId());
            }
        } else if("BUTTON".equals(request.type())) throw error(400,"按钮必须选择所属页面");
        if(id!=null && !menu.getType().equals(request.type()) && menus().stream().anyMatch(m->id.equals(m.getParentId())))
            throw error(409,"请先移走子菜单再修改类型");
        if(permission!=null && menus().stream().anyMatch(m->permission.equals(m.getPermission()) && !m.getId().equals(id)))
            throw error(409,"权限标识已存在");
        menu.setParentId(request.parentId()); menu.setName(request.name().trim()); menu.setType(request.type());
        menu.setPath(path); menu.setPermission(permission); menu.setSortOrder(request.sortOrder()); menu.setEnabled(request.enabled());
        if(id==null) mapper.insertMenu(menu); else mapper.updateMenu(menu);
        return mapper.menu(menu.getId());
    }
    @Transactional
    public void deleteMenu(long id) {
        mapper.lock();
        Menu menu=mapper.menu(id);
        if(menu==null) throw error(404,"菜单不存在");
        if(menu.isBuiltin()) throw error(400,"内置菜单不能删除");
        if(menus().stream().anyMatch(m->Objects.equals(id,m.getParentId()))) throw error(409,"请先删除子菜单");
        mapper.clearMenuGrants(id); mapper.deleteMenu(id);
    }
    private static String blankToNull(String s) { return s==null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException error(int code,String message) { return new ResponseStatusException(HttpStatus.valueOf(code),message); }
}
