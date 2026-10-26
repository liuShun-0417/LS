package com.example.demo.system.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 密码编码与校验。
 * 新密码使用 BCrypt；旧数据中的单次/三次 MD5 仍可登录，并在下次成功登录时自动升级为 BCrypt。
 */
public final class PasswordCodec {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final Pattern MD5_HEX = Pattern.compile("^[0-9a-fA-F]{32}$");

    private PasswordCodec() {
    }

    /** 使用 BCrypt 计算新密码哈希。 */
    public static String hash(String password) {
        return ENCODER.encode(password);
    }

    /** 校验密码：BCrypt 或旧 MD5（单次/三次）均可通过。 */
    public static boolean matches(String candidate, String stored) {
        if (stored == null) return false;
        if (isBcrypt(stored)) {
            return ENCODER.matches(candidate, stored);
        }
        if (MD5_HEX.matcher(stored).matches()) {
            return md5(md5(md5(candidate))).equalsIgnoreCase(stored)
                    || md5(candidate).equalsIgnoreCase(stored);
        }
        return false;
    }

    /** 是否为旧版 MD5 存储，需要在登录成功后升级为 BCrypt。 */
    public static boolean isLegacy(String stored) {
        return stored != null && MD5_HEX.matcher(stored).matches();
    }

    private static boolean isBcrypt(String stored) {
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
    }

    private static String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
