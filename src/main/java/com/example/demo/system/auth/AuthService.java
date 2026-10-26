package com.example.demo.system.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.demo.system.dto.CreateUserRequest;
import com.example.demo.system.dto.UserResponse;
import com.example.demo.system.entity.User;
import com.example.demo.system.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AuthService {
    private final UserMapper mapper;
    private final com.example.demo.system.rbac.RbacService rbac;

    public AuthService(UserMapper mapper, com.example.demo.system.rbac.RbacService rbac) {
        this.mapper = mapper;
        this.rbac = rbac;
    }

    @org.springframework.transaction.annotation.Transactional
    public AuthResponse register(CreateUserRequest request) {
        if(request.roleId()!=null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"注册不能指定角色");
        String account = request.account().trim();
        if (mapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getAccount, account)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
        }
        var user = new User();
        user.setName(request.name());
        user.setAccount(account);
        user.setPasswd(PasswordCodec.hash(request.passwd()));
        mapper.insert(user);
        rbac.bind(user.getId(),null);
        return signedIn(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = mapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, request.account().trim()), true);
        if (user == null) {
            throw invalidCredentials();
        }
        if (!PasswordCodec.matches(request.passwd(), user.getPasswd())) {
            if (!PasswordCodec.matchesLegacy(request.passwd(), user.getPasswd())) throw invalidCredentials();
            int upgraded = mapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .eq(User::getPasswd, user.getPasswd())
                    .set(User::getPasswd, PasswordCodec.hash(request.passwd())));
            if (upgraded != 1) throw invalidCredentials();
        }
        return signedIn(user);
    }

    public UserResponse currentUser() {
        int id = StpUtil.getLoginIdAsInt();
        User user = mapper.selectById(id);
        if (user == null) {
            StpUtil.logout();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        return toResponse(user);
    }

    private AuthResponse signedIn(User user) {
        StpUtil.login(user.getId());
        return new AuthResponse(StpUtil.getTokenName(), StpUtil.getTokenValue(), toResponse(user));
    }

    private UserResponse toResponse(User user) {
        var role=rbac.userRole(user.getId());
        return new UserResponse(user.getId(), user.getName(), user.getAccount(),role==null?null:role.getId(),role==null?null:role.getName());
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }
}
