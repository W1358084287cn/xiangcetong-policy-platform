package com.xiangcetong.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务异常与错误码：静态工厂的 code / httpStatus / message 映射。
 */
class BizExceptionTest {

    @Test
    void badParamUses40001AndHttp200() {
        BizException e = BizException.badParam("字段非法");
        assertEquals(ErrorCodes.BAD_PARAM, e.getCode());
        assertEquals(200, e.getHttpStatus());
        assertEquals("字段非法", e.getMessage());
    }

    @Test
    void unauthorizedUses40101AndHttp401() {
        BizException e = BizException.unauthorized("未登录");
        assertEquals(ErrorCodes.UNAUTHORIZED, e.getCode());
        assertEquals(401, e.getHttpStatus());
    }

    @Test
    void forbiddenUses40301AndHttp403() {
        BizException e = BizException.forbidden("无权限");
        assertEquals(ErrorCodes.FORBIDDEN, e.getCode());
        assertEquals(403, e.getHttpStatus());
    }

    @Test
    void notFoundUses40401AndHttp404() {
        BizException e = BizException.notFound("不存在");
        assertEquals(ErrorCodes.NOT_FOUND, e.getCode());
        assertEquals(404, e.getHttpStatus());
    }

    @Test
    void constructorWithDataCarriesData() {
        BizException e = new BizException(ErrorCodes.PROFILE_INCOMPLETE, "资料不完整", 200, "missing");
        assertEquals("missing", e.getData());
        assertEquals(ErrorCodes.PROFILE_INCOMPLETE, e.getCode());
    }

    @Test
    void errorCodesAreStable() {
        assertEquals(0, ErrorCodes.OK);
        assertEquals(40001, ErrorCodes.BAD_PARAM);
        assertEquals(40101, ErrorCodes.UNAUTHORIZED);
        assertEquals(40301, ErrorCodes.FORBIDDEN);
        assertEquals(40401, ErrorCodes.NOT_FOUND);
        assertEquals(40901, ErrorCodes.CONFLICT);
        assertEquals(42201, ErrorCodes.BIZ_RULE);
        assertEquals(42202, ErrorCodes.PROFILE_INCOMPLETE);
        assertEquals(50001, ErrorCodes.SERVER_ERROR);
    }
}
