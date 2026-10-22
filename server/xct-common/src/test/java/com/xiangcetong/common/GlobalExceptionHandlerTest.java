package com.xiangcetong.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全局异常处理：业务异常 HTTP 状态、参数校验消息提取、兜底 500 不泄露堆栈。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBizMapsHttpStatusAndCode() {
        BizException e = new BizException(ErrorCodes.FORBIDDEN, "无权限", 403);
        ResponseEntity<ApiResponse<Object>> resp = handler.handleBiz(e);
        assertEquals(403, resp.getStatusCode().value());
        assertEquals(ErrorCodes.FORBIDDEN, resp.getBody().getCode());
        assertEquals("无权限", resp.getBody().getMessage());
    }

    @Test
    void handleBizCarriesData() {
        BizException e = new BizException(ErrorCodes.PROFILE_INCOMPLETE, "资料不完整", 200, "missing");
        ResponseEntity<ApiResponse<Object>> resp = handler.handleBiz(e);
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("missing", resp.getBody().getData());
    }

    @Test
    void handleValidExtractsFieldMessage() {
        BindException be = new BindException(new Object(), "req");
        be.addError(new FieldError("req", "name", "姓名不能为空"));
        ResponseEntity<ApiResponse<Void>> resp = handler.handleValid(be);
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(ErrorCodes.BAD_PARAM, resp.getBody().getCode());
        assertEquals("姓名不能为空", resp.getBody().getMessage());
    }

    @Test
    void handleNotReadableReturnsBadParam() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotReadable(new HttpMessageNotReadableException("bad json"));
        assertEquals(ErrorCodes.BAD_PARAM, resp.getBody().getCode());
    }

    @Test
    void handleUploadSizeReturns413() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleUploadSize(new MaxUploadSizeExceededException(1024));
        assertEquals(413, resp.getStatusCode().value());
        assertEquals(ErrorCodes.BAD_PARAM, resp.getBody().getCode());
    }

    @Test
    void handleOtherReturns500WithoutStackTrace() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleOther(new RuntimeException("boom"));
        assertEquals(500, resp.getStatusCode().value());
        assertEquals(ErrorCodes.SERVER_ERROR, resp.getBody().getCode());
        assertEquals("系统繁忙，请稍后重试", resp.getBody().getMessage());
    }
}
