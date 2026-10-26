package com.example.demo.system.controller;

import com.example.demo.system.dto.CreateUserRequest;
import com.example.demo.system.dto.UpdateUserRequest;
import com.example.demo.system.dto.UserPage;
import com.example.demo.system.dto.UserResponse;
import com.example.demo.system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import cn.dev33.satoken.stp.StpUtil;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public UserPage list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "") String keyword) {
        StpUtil.checkPermission("user:read");
        return service.list(page, keyword);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable int id) {
        StpUtil.checkPermission("user:read");
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        StpUtil.checkPermission("user:create");
        UserResponse user = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable int id, @Valid @RequestBody UpdateUserRequest request) {
        StpUtil.checkPermission("user:update");
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        StpUtil.checkPermission("user:delete");
        service.delete(id);
    }
}
