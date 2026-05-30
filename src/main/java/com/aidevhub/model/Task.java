package com.aidevhub.model;

import com.aidevhub.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    private Long projectId;
    private String title;
    private String description;
    private String acceptance;
    private String status;
    private String priority;
    private String source;
    private String aiModel;
    private String targetBranch;

    @TableField(exist = false)
    private Project project;
}
