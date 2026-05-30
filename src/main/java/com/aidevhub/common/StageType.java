package com.aidevhub.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 流水线阶段类型
 */
public enum StageType {

    INIT("初始化"),
    PLAN("方案规划"),
    CODE("代码生成"),
    REVIEW("代码审查"),
    TEST("测试"),
    DEPLOY("部署"),
    CUSTOM("自定义脚本");

    @EnumValue
    @JsonValue
    private final String description;

    StageType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
