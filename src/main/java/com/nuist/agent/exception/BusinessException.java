package com.nuist.agent.exception;
import com.nuist.agent.common.ErrorCode;
import lombok.Getter;

//  业务异常（可预期异常）
// service层可以抛出该异常 并且通过全局异常处理器进行统一处理并返回为R响应
@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;
    public BusinessException(Integer code,String message){
        super(message);
        this.code = code;
    }
    //    错误码应该是有范围的 错误码就应该通过枚举类进行定义
    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}