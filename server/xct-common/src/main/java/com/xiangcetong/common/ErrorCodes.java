package com.xiangcetong.common;

/**
 * 全局业务错误码（与 docs/03-api.md 对齐）。
 * code=0 表示成功；其余为业务/鉴权错误。
 */
public final class ErrorCodes {

    public static final int OK = 0;
    /** 参数校验失败 */
    public static final int BAD_PARAM = 40001;
    /** 未认证 / Token 无效 */
    public static final int UNAUTHORIZED = 40101;
    /** 无权限（角色或数据权限） */
    public static final int FORBIDDEN = 40301;
    /** 资源不存在 */
    public static final int NOT_FOUND = 40401;
    /** 冲突（如手机号重复） */
    public static final int CONFLICT = 40901;
    /** 业务规则不满足（状态机、下架政策等） */
    public static final int BIZ_RULE = 42201;
    /** 村民资料不完整，无法做政策匹配 */
    public static final int PROFILE_INCOMPLETE = 42202;
    /** 服务器内部错误 */
    public static final int SERVER_ERROR = 50001;

    private ErrorCodes() {
    }
}
