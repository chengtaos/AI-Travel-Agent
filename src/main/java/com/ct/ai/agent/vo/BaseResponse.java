package com.ct.ai.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全局统一响应结果封装类
 * 用于规范所有接口的响应格式，避免响应结构混乱，支持泛型数据类型（适配不同业务场景的返回数据）
 *
 * @param <T> 响应数据的泛型类型（可根据业务需求指定，如 String、List、自定义VO等）
 */
@Data
@Schema(description = "全局统一响应结果：包含状态码、响应数据、提示消息，所有接口均返回此格式")
public class BaseResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = -8940196742319902606L;

    /**
     * 状态码
     * 0：成功；非0：失败（不同非0值可代表不同错误类型，如1001参数错误、2001业务异常等）
     */
    @Schema(description = "状态码（0=成功，非0=失败，具体错误码见业务文档）", example = "0")
    private int code;

    /**
     * 响应数据
     * 泛型类型，成功时返回具体业务数据（如列表、详情对象），失败时可为 null
     */
    @Schema(description = "响应数据（成功时返回业务数据，失败时为null）")
    private T data;

    /**
     * 响应消息
     * 成功时返回友好提示（如“操作成功”），失败时返回具体错误原因（如“参数不能为空”），便于前端提示用户
     */
    @Schema(description = "响应消息（成功时为操作提示，失败时为错误原因）", example = "操作成功")
    private String message;


    /**
     * 全参构造器
     * 用于手动指定状态码、数据、消息，灵活适配特殊业务场景
     *
     * @param code    状态码
     * @param data    响应数据
     * @param message 响应消息
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    /**
     * 简化构造器（默认消息为空）
     * 适用于无需自定义消息的场景（如成功时默认消息由静态方法统一控制）
     *
     * @param code 状态码
     * @param data 响应数据
     */
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }


    /**
     * 静态工厂方法：构建“成功”响应（默认消息+自定义数据）
     *
     * @param data 成功时需返回的业务数据（如查询结果、新增后的对象）
     * @param <T>  数据泛型类型
     * @return 统一成功响应对象（code=0，message=操作成功，data=业务数据）
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "操作成功");
    }

    /**
     * 静态工厂方法：构建“成功”响应（自定义消息+自定义数据）
     * 适用于需要自定义成功提示的场景（如“数据导入成功，共100条”）
     *
     * @param data    成功时需返回的业务数据
     * @param message 自定义成功提示消息
     * @param <T>     数据泛型类型
     * @return 统一成功响应对象（code=0，message=自定义消息，data=业务数据）
     */
    public static <T> BaseResponse<T> success(T data, String message) {
        return new BaseResponse<>(0, data, message);
    }

    /**
     * 静态工厂方法：构建“失败”响应（自定义错误码+自定义错误消息）
     * 适用于所有失败场景（参数错误、业务异常、系统异常等），通过错误码区分失败类型
     *
     * @param code    自定义错误码（如1001=参数错误，2001=权限不足，3001=系统异常）
     * @param message 具体错误原因（需清晰明确，便于前端提示用户或排查问题）
     * @param <T>     数据泛型类型（失败时数据为null，泛型仅为满足类结构）
     * @return 统一失败响应对象（code=自定义错误码，message=错误原因，data=null）
     */
    public static <T> BaseResponse<T> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }

    /**
     * 扩展静态工厂方法：构建“参数错误”响应（默认错误码+自定义消息）
     * 封装常见错误场景，减少重复编码（如参数为空、格式非法时直接调用）
     *
     * @param message 具体参数错误原因（如“用户名不能为空”）
     * @param <T>     数据泛型类型
     * @return 参数错误响应对象（code=1001，message=错误原因，data=null）
     */
    public static <T> BaseResponse<T> paramError(String message) {
        return new BaseResponse<>(1001, null, message);
    }

    /**
     * 扩展静态工厂方法：构建“业务异常”响应（默认错误码+自定义消息）
     * 适用于业务逻辑校验失败的场景（如“余额不足”“订单已取消”）
     *
     * @param message 具体业务错误原因
     * @param <T>     数据泛型类型
     * @return 业务异常响应对象（code=2001，message=错误原因，data=null）
     */
    public static <T> BaseResponse<T> businessError(String message) {
        return new BaseResponse<>(2001, null, message);
    }

    /**
     * 扩展静态工厂方法：构建“系统异常”响应（默认错误码+固定消息）
     * 适用于未知系统错误（如数据库异常、第三方服务调用失败），避免暴露敏感信息
     *
     * @param <T> 数据泛型类型
     * @return 系统异常响应对象（code=5001，message=系统繁忙，请稍后再试，data=null）
     */
    public static <T> BaseResponse<T> systemError() {
        return new BaseResponse<>(5001, null, "系统繁忙，请稍后再试");
    }
}