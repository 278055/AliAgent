package com.bn.aliagent.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应封装
 *
 * @param <T> data 类型
 */
@Data
@NoArgsConstructor
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return restResult(null, 200, "操作成功");
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, 200, "操作成功");
    }

    public static <T> R<T> ok(String msg, T data) {
        return restResult(data, 200, msg);
    }

    public static <T> R<T> ok(String msg) {
        return restResult(null, 200, msg);
    }

    public static <T> R<T> fail() {
        return restResult(null, 500, "操作失败");
    }

    public static <T> R<T> fail(String msg) {
        return restResult(null, 500, msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        return restResult(null, code, msg);
    }

    public static <T> R<T> fail(String msg, T data) {
        return restResult(data, 500, msg);
    }

    public static <T> R<T> status(int code, String msg) {
        return restResult(null, code, msg);
    }

    public static <T> R<T> status(int code, String msg, T data) {
        return restResult(data, code, msg);
    }

    private static <T> R<T> restResult(T data, int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setData(data);
        r.setMsg(msg);
        return r;
    }

    public static <T> boolean isSuccess(R<T> ret) {
        return ret != null && ret.getCode() == 200;
    }

    public static <T> boolean isError(R<T> ret) {
        return !isSuccess(ret);
    }
}
