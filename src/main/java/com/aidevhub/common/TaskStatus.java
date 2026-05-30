package com.aidevhub.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 任务状态枚举：PENDING→PLANNING→CODING→REVIEWING→PR_OPEN→DEPLOYED，
 * FAILED可从任意状态转入，终态(DEPLOYED/FAILED)不可再变更
 */
public enum TaskStatus {

    PENDING("待处理"),
    PLANNING("规划中"),
    CODING("编码中"),
    REVIEWING("审查中"),
    PR_OPEN("PR已打开"),
    DEPLOYED("已部署"),
    FAILED("失败");

    @EnumValue
    @JsonValue
    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前状态是否为终态
     */
    public boolean isTerminal() {
        return this == DEPLOYED || this == FAILED;
    }

    /**
     * 判断是否可以转换到目标状态
     */
    public boolean canTransitionTo(TaskStatus target) {
        if (this.isTerminal()) {
            return false;
        }
        if (target == FAILED) {
            return true;
        }
        return target.ordinal() == this.ordinal() + 1;
    }
}
