package com.aidevhub.model;

import com.aidevhub.common.BaseEntity;
import com.aidevhub.common.StageType;
import com.aidevhub.common.TaskStatus;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 执行记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("execution")
public class Execution extends BaseEntity {

    private Long taskId;

    private StageType stageType;

    private TaskStatus status;

    private String output;

    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
