package com.trip.common.exception;

import com.trip.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常（非受检），携带业务状态码。
 * 用法：throw new BizException(ResultCode.ROUTE_NOT_FOUND);
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}