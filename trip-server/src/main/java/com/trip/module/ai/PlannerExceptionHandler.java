package com.trip.module.ai;

import com.trip.common.exception.BizException;
import com.trip.common.result.R;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Before SSE headers are committed, validation/admission errors use the normal JSON envelope. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=PlannerController.class)
public class PlannerExceptionHandler {
    @ExceptionHandler({BizException.class,MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<R<Void>> handle(Exception exception) {
        int code=400;String message="请求体格式错误";
        if(exception instanceof BizException e) { code=e.getCode();message=e.getMessage(); }
        else if(exception instanceof MethodArgumentNotValidException e)message=e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f->f.getField()+" "+f.getDefaultMessage()).orElse("请求参数格式错误");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(R.fail(code,message));
    }
}
