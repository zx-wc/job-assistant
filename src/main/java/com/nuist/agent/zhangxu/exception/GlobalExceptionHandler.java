package com.nuist.agent.zhangxu.exception;

import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.zhangxu.entity.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    //     处理自定义业务异常
    @ExceptionHandler(BusinessException.class)
    public R handleBusinessException(BusinessException e){
        log.warn("业务异常:code{},message={}",e.getCode(),e.getMessage());
        return R.error(e.getCode(),e.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public R handleIllegalArgumentException(IllegalArgumentException e){
        log.warn("参数异常:{}",e.getMessage());
        return R.error(400,e.getMessage());
    }
    // 处理静态资源不存在(如 favicon.ico),避免被兜底异常处理器当作系统异常打出 ERROR 堆栈
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R handleNoResourceFoundException(NoResourceFoundException e){
        log.debug("静态资源不存在:{}",e.getResourcePath());
        return R.error(404,"资源不存在");
    }
    @ExceptionHandler(Exception.class)
    public R handleException(Exception e){
        log.error("系统异常",e);
        return R.error(ErrorCode.FAIL);
    }

}
