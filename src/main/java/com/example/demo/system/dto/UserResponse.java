package com.example.demo.system.dto;

/** 用户响应不包含密码或密码摘要。 */
public record UserResponse(Integer id, String name, String account, Long roleId, String roleName) {
    public UserResponse(Integer id,String name,String account) { this(id,name,account,null,null); }
}
