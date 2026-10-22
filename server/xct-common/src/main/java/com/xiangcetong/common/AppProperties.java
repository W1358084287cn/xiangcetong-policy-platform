package com.xiangcetong.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取 application.yml 中 xiangcetong.* 配置。
 */
@Component
@ConfigurationProperties(prefix = "xiangcetong")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Redis redis = new Redis();
    private final Upload upload = new Upload();
    private final PolicyMatch policyMatch = new PolicyMatch();

    public Jwt getJwt() {
        return jwt;
    }

    public Redis getRedis() {
        return redis;
    }

    public Upload getUpload() {
        return upload;
    }

    public PolicyMatch getPolicyMatch() {
        return policyMatch;
    }

    public static class Jwt {
        private String secret = "change-me";
        private long expireSeconds = 86400;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = ***;
        }

        public long getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(long expireSeconds) {
            this.expireSeconds = expireSeconds;
        }
    }

    public static class Redis {
        private String tokenPrefix = "xiangcetong:token:";

        public String getTokenPrefix() {
            return tokenPrefix;
        }

        public void setTokenPrefix(String tokenPrefix) {
            this.tokenPrefix = ***;
        }
    }

    public static class Upload {
        private String dir = "uploads";
        private String urlPrefix = "/uploads";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String getUrlPrefix() {
            return urlPrefix;
        }

        public void setUrlPrefix(String urlPrefix) {
            this.urlPrefix = urlPrefix;
        }
    }

    /** 政策匹配开关（匹配逻辑为 MySQL 多条件 WHERE，不再有加权阈值） */
    public static class PolicyMatch {
        private boolean hideNoneByDefault = true;

        public boolean isHideNoneByDefault() { return hideNoneByDefault; }
        public void setHideNoneByDefault(boolean hideNoneByDefault) { this.hideNoneByDefault = hideNoneByDefault; }
    }
}
