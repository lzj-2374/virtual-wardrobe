package com.liuzhangjie.wardrobe.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        ResultCode rc = e.getResultCode();
        return Result.fail(rc.getCode(), rc.getMessage());
    }

}
