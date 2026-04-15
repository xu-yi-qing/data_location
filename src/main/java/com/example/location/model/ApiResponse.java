package com.example.location.model;

/**
 * 统一 API 响应包装类
 *
 * <p>所有接口均返回此结构：
 * <pre>
 * {
 *   "code":    0,       // 0 表示成功，非0表示业务错误
 *   "data":    ...,     // 业务数据，成功时有值
 *   "message": null     // 错误描述，成功时为 null（Jackson 配置了 non_null 不序列化）
 * }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> {

    /** 响应码，0 表示成功 */
    private int code;

    /** 错误描述，成功时为 null */
    private String message;

    /** 业务数据 */
    private T data;

    private ApiResponse() {}

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=0 的响应对象
     */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.data = data;
        return r;
    }

    /**
     * 构造错误响应。
     *
     * @param code    错误码（非0）
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 含错误信息的响应对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
