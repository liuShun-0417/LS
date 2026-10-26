package com.example.demo.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.system.auth.PasswordCodec;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.system.dto.CreateUserRequest;
import com.example.demo.system.dto.UpdateUserRequest;
import com.example.demo.system.dto.UserPage;
import com.example.demo.system.dto.UserResponse;
import com.example.demo.system.entity.User;
import com.example.demo.system.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {
    private static final int PAGE_SIZE = 20;
    private final UserMapper mapper;
    private final com.example.demo.system.rbac.RbacService rbac;

    public UserService(UserMapper mapper, com.example.demo.system.rbac.RbacService rbac) {
        this.mapper = mapper;
        this.rbac = rbac;
    }

    public UserPage list(int page, String keyword) {
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码必须大于 0");
        }
        var query = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getName, User::getAccount)
                .orderByDesc(User::getId);
        if(keyword.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"搜索内容最长 100 字符");
        if(!keyword.isBlank()) query.and(q->q.like(User::getName,keyword.trim()).or().like(User::getAccount,keyword.trim()));
        var result = mapper.selectPage(new Page<>(page, PAGE_SIZE), query);
        List<UserResponse> users = rbac.userResponses(result.getRecords().stream().map(User::getId).toList());
        return new UserPage(users, page, PAGE_SIZE, result.getTotal(), result.getPages());
    }

    public UserResponse get(int id) {
        User user = mapper.selectById(id);
        if (user == null) throw notFound();
        return toResponse(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if(request.roleId()!=null) StpUtil.checkPermission("user:assign-role");
        String account = request.account().trim();
        checkAccountAvailable(account, null);
        User user = new User();
        user.setName(request.name());
        user.setAccount(account);
        user.setPasswd(PasswordCodec.hash(request.passwd()));
        mapper.insert(user);
        rbac.bind(user.getId(),request.roleId());
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(int id, UpdateUserRequest request) {
        rbac.protectAdministrator(id);
        if(request.roleId()!=null) { StpUtil.checkPermission("user:assign-role"); rbac.bind(id,request.roleId()); }
        String account = request.account().trim();
        checkAccountAvailable(account, id);
        User user = new User();
        user.setId(id);
        user.setName(request.name());
        user.setAccount(account);
        if (StringUtils.hasText(request.passwd())) {
            user.setPasswd(PasswordCodec.hash(request.passwd()));
        }
        if (mapper.updateById(user) == 0) {
            throw notFound();
        }
        UserResponse updated = get(id);
        if (StringUtils.hasText(request.passwd())) StpUtil.logout(id);
        return updated;
    }

    @Transactional
    public void delete(int id) {
        rbac.protectAdministrator(id);
        rbac.protectLastAdmin(id);
        if (mapper.deleteById(id) == 0) {
            throw notFound();
        }
        StpUtil.logout(id);
        rbac.removeBinding(id);
    }

    private UserResponse toResponse(User user) {
        var role=rbac.userRole(user.getId());
        return new UserResponse(user.getId(), user.getName(), user.getAccount(),role==null?null:role.getId(),role==null?null:role.getName());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    }

    private void checkAccountAvailable(String account, Integer ignoredId) {
        var query = new LambdaQueryWrapper<User>().eq(User::getAccount, account);
        if (ignoredId != null) query.ne(User::getId, ignoredId);
        if (mapper.selectCount(query) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
        }
    }
}
