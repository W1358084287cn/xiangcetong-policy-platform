package com.xiangcetong.common;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由 AuthInterceptor 在请求进入时写入，结束后清理，避免线程复用串号。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /** 绑定当前请求的登录用户 */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 取出当前用户；未登录返回 null */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 必须已登录，否则抛 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized("未登录或会话已失效");
        }
        return user;
    }

    /** 要求角色之一，否则 403 */
    public static LoginUser requireRole(String... roles) {
        LoginUser user = require();
        for (String role : roles) {
            if (role.equals(user.getRole())) {
                return user;
            }
        }
        throw BizException.forbidden("无权限访问该资源");
    }

    /** 请求结束必须调用，防止线程池复用污染 */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 登录会话快照（也写入 Redis）。
     */
    public static class LoginUser {
        private Long userId;
        private String phone;
        private String name;
        private String role;
        private String countyCode;
        private String townCode;
        private String villageCode;
        private String jti;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getCountyCode() {
            return countyCode;
        }

        public void setCountyCode(String countyCode) {
            this.countyCode = countyCode;
        }

        public String getTownCode() {
            return townCode;
        }

        public void setTownCode(String townCode) {
            this.townCode = townCode;
        }

        public String getVillageCode() {
            return villageCode;
        }

        public void setVillageCode(String villageCode) {
            this.villageCode = villageCode;
        }

        public String getJti() {
            return jti;
        }

        public void setJti(String jti) {
            this.jti = jti;
        }
    }
}
