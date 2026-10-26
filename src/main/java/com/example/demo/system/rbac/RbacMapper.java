package com.example.demo.system.rbac;

import org.apache.ibatis.annotations.*;
import java.util.List;
import com.example.demo.system.rbac.RbacModels.*;

@Mapper
public interface RbacMapper {
    @Select("SELECT r.*, (SELECT COUNT(*) FROM sys_user_role ur JOIN `user` u ON u.id=ur.user_id WHERE ur.role_id=r.id) AS user_count FROM sys_role r ORDER BY r.id")
    List<Role> roles();
    @Select("SELECT * FROM sys_role WHERE id=#{id}") Role role(long id);
    @Select("SELECT * FROM sys_role WHERE code=#{code}") Role roleByCode(String code);
    @Select("SELECT id FROM sys_role WHERE code='super_admin' FOR UPDATE") Long lock();
    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId}") Role userRole(int userId);
    @Select("SELECT r.* FROM `user` u LEFT JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=COALESCE(ur.role_id,2) WHERE u.id=#{userId}") Role effectiveRole(int userId);
    @Select("<script>SELECT u.id,u.name,u.account,r.id AS role_id,r.name AS role_name FROM `user` u LEFT JOIN sys_user_role ur ON ur.user_id=u.id LEFT JOIN sys_role r ON r.id=COALESCE(ur.role_id,2) WHERE u.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<com.example.demo.system.dto.UserResponse> userResponses(@Param("ids") List<Integer> ids);
    @Select("SELECT COUNT(*) FROM sys_user_role ur JOIN `user` u ON u.id=ur.user_id WHERE ur.role_id=#{roleId}") long userCount(long roleId);
    @Insert("INSERT INTO sys_role(name,code,description,enabled,builtin) VALUES(#{name},#{code},#{description},#{enabled},false)")
    @Options(useGeneratedKeys=true,keyProperty="id") void insertRole(Role role);
    @Update("UPDATE sys_role SET name=#{name},code=#{code},description=#{description},enabled=#{enabled} WHERE id=#{id}") void updateRole(Role role);
    @Delete("DELETE FROM sys_role WHERE id=#{id}") void deleteRole(long id);
    @Select("SELECT * FROM sys_menu ORDER BY sort_order,id") List<Menu> menus();
    @Select("SELECT * FROM sys_menu WHERE id=#{id}") Menu menu(long id);
    @Insert("INSERT INTO sys_menu(parent_id,name,type,path,permission,sort_order,enabled,builtin) VALUES(#{parentId},#{name},#{type},#{path},#{permission},#{sortOrder},#{enabled},false)")
    @Options(useGeneratedKeys=true,keyProperty="id") void insertMenu(Menu menu);
    @Update("UPDATE sys_menu SET parent_id=#{parentId},name=#{name},type=#{type},path=#{path},permission=#{permission},sort_order=#{sortOrder},enabled=#{enabled} WHERE id=#{id}") void updateMenu(Menu menu);
    @Delete("DELETE FROM sys_menu WHERE id=#{id}") void deleteMenu(long id);
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id=#{roleId}") List<Long> grants(long roleId);
    @Delete("DELETE FROM sys_role_menu WHERE role_id=#{roleId}") void clearGrants(long roleId);
    @Delete("DELETE FROM sys_role_menu WHERE menu_id=#{menuId}") void clearMenuGrants(long menuId);
    @Insert("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(#{roleId},#{menuId})") void grant(long roleId,long menuId);
    @Delete("DELETE FROM sys_user_role WHERE user_id=#{userId}") void unbind(int userId);
    @Insert("INSERT INTO sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})") void bind(int userId,long roleId);
}
