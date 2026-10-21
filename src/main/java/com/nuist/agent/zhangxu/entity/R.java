package com.nuist.agent.zhangxu.entity;

import com.nuist.agent.zhangxu.common.ErrorCode;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

// 统一API响应类
@Data
public class R {
    private Integer code; // 自定义响应码
    private String msg;// 后端反馈给前端的消息
    private Map<String,Object> dataMap = new HashMap<>();
    // 让构造方法私有 不允许通过外部new方式创建,而是统一通过静态工厂方法创建
    private R(){}
    // 静态工厂方法
    // 返回一个无数据但是成功的R对象
    public static R ok(){
        R r = new R();
        r.setCode(ErrorCode.SUCCESS.getCode());
        r.setMsg(ErrorCode.SUCCESS.getMessage());
        return r;
    }
    ///  失败场景
    public static R error(){
        R r = new R();
        r.setCode(ErrorCode.FAIL.getCode());
        r.setMsg(ErrorCode.FAIL.getMessage());
        return r;
    }
    // 指定错误码的失败场景
    public static R error(ErrorCode errorCode){
        R r = new R();
        r.setCode(errorCode.getCode());
        r.setMsg(errorCode.getMessage());
        return r;
    }
    // 指定错误码和错误消息的失败场景
    public static R error(Integer code,String msg){
        R r = new R();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
    // 链式添加数据
    public R data(String key,Object val){
        this.dataMap.put(key,val);
        return this;
    }
    // 批量添加数据
    public R data(Map<String,Object> maps){
        this.dataMap.putAll(maps);
        return this;
    }



}
