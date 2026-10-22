package com.xiangcetong.common;

/**
 * 业务异常：抛出后由全局处理器转成统一 JSON。
 * httpStatus 控制 HTTP 状态码（鉴权用 401/403，业务多用 200）。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final int httpStatus;
    private final Object data;

    public BizException(int code, String message) {
        this(code, message, 200, null);
    }

    public BizException(int code, String message, int httpStatus) {
        this(code, message, httpStatus, null);
    }

    public BizException(int code, String message, int httpStatus, Object data) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Object getData() {
        return data;
    }

    public static BizException badParam(String message) {
        return new BizException(ErrorCodes.BAD_PARAM, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(ErrorCodes.UNAUTHORIZED, message, 401);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCodes.FORBIDDEN, message, 403);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCodes.NOT_FOUND, message, 404);
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }

    public static BizException serverError(String message) {
        return new BizException(ErrorCodes.SERVER_ERROR, message, 500);
    }
}