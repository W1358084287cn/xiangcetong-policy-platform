package com.xiangcetong.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：把各类异常转成统一 ApiResponse。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 处理主动抛出的业务异常（可带 data，如资料缺失字段） */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.fail(e.getCode(), e.getMessage(), e.getData()));
    }

    /** 处理 @Valid 校验失败 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValid(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            msg = manv.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException be && be.getBindingResult().getFieldError() != null) {
            msg = be.getBindingResult().getFieldError().getDefaultMessage();
        }
        return ResponseEntity.ok(ApiResponse.fail(ErrorCodes.BAD_PARAM, msg));
    }

    /** JSON 体无法解析：返回可读原因，避免冒充 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析: {}", e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage() : e.getMessage());
        return ResponseEntity.ok(ApiResponse.fail(ErrorCodes.BAD_PARAM, "请求体格式错误或字段不合法"));
    }

    /** 上传超过大小限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413)
                .body(ApiResponse.fail(ErrorCodes.BAD_PARAM, "上传文件超过 5MB 限制"));
    }

    /** 兜底：未预期异常记日志，避免把堆栈暴露给前端 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(500)
                .body(ApiResponse.fail(ErrorCodes.SERVER_ERROR, "系统繁忙，请稍后重试"));
    }
}
