package com.xiangcetong.common;

/**
 * 统一响应体：{code, message, data}。
 * 前端统一看 code==0 判断成功。
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功且带数据 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCodes.OK, "ok", data);
    }

    /** 成功无数据 */
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /** 失败响应 */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /** 失败但携带附加数据（如 missingFields） */
    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
