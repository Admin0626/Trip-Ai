package com.trip.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "操作冲突，请刷新后重试"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    SYSTEM_ERROR(500, "服务器内部错误"),

    LOGIN_FAILED(1001, "用户名或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已被禁用"),

    ROUTE_NOT_FOUND(2001, "路线不存在或已下架"),
    BOOKING_FULL(2002, "预约人数已满"),
    BOOKING_CLOSED(2003, "预约截止日期已过"),

    BOOKING_DUP(2004, "同一路线同一出行日期已预约，请勿重复提交"),
    COMMENT_TOO_MANY(2005, "每人每路线每天最多评论 3 条"),
    PLAN_LIMIT(2006, "规划数量已达上限（50 条），请先删除旧规划"),

    AI_UNAVAILABLE(3001, "AI 服务暂时不可用，已为你切换到基础推荐"),
    AI_INTENT_FAILED(3002, "AI 解析失败，请换个说法试试"),
    AI_NO_KNOWLEDGE(3003, "知识库暂无相关内容，请换个问法或联系管理员补充资料");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
