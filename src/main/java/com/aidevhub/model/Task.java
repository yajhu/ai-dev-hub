package com.aidevhub.model;

import com.aidevhub.common.BaseEntity;
import com.aidevhub.common.StageType;
import com.aidevhub.common.TaskStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    private Long projectId;

    private String name;

    private String description;

    private TaskStatus status;

    private StageType stageType;

    private Integer priority;

    private String assignedTo;

    private String branchName;

    private String prUrl;

    private String errorMessage;

    @TableField(exist = false)
    private Project project;
}
