package com.aidevhub.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一响应包装类
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 失败响应（指定code和message）
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 失败响应（默认500状态码）
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 错误响应（默认500状态码）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 分页成功响应
     *
     * @param list  数据列表
     * @param total 总条数
     * @param <T>   数据类型
     * @return 包含分页信息的Result
     */
    public static <T> Result<Map<String, Object>> paginated(List<T> list, long total) {
        Map<String, Object> map = new HashMap<>();
        map.put("list", list);
        map.put("total", total);
        return new Result<>(200, "success", map);
    }
}
