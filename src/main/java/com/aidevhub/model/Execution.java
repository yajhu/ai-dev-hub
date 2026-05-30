package com.aidevhub.model;

import com.aidevhub.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("execution")
public class Execution extends BaseEntity {

    private Long taskId;
    private String stage;
    private String status;
    private String output;
    private String sonarReport;
    private String prUrl;
    private String deployUrl;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
