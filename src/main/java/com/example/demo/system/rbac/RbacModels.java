package com.example.demo.system.rbac;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

public final class RbacModels {
    private RbacModels() {}

    @Getter @Setter
    public static class Role {
        private Long id;
        private String name;
        private String code;
        private String description;
        private boolean enabled;
        private boolean builtin;
        private long userCount;
    }

    @Getter @Setter
    public static class Menu {
        private Long id;
        private Long parentId;
        private String name;
        private String type;
        private String path;
        private String permission;
        private int sortOrder;
        private boolean enabled;
        private boolean builtin;
    }

    public record RoleRequest(@NotBlank @Size(max=100) String name,
            @NotBlank @Pattern(regexp="[a-zA-Z][a-zA-Z0-9_:-]{0,63}") String code,
            @Size(max=255) String description, @NotNull Boolean enabled) {}

    public record MenuRequest(@Positive Long parentId, @NotBlank @Size(max=100) String name,
            @NotNull @Pattern(regexp="DIRECTORY|MENU|BUTTON") String type,
            @Size(max=200) String path, @Size(max=100) String permission,
            @Min(0) @Max(9999) int sortOrder, @NotNull Boolean enabled) {}

    public record GrantRequest(@NotNull @Size(max=500) List<@NotNull @Positive Long> menuIds) {}
    public record BindRequest(@NotNull @Positive Long roleId) {}
    public record Access(Role role, List<String> permissions, List<Menu> menus) {}
}
