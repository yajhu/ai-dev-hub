package com.aidevhub.model;

import com.aidevhub.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("quality_gate")
public class QualityGate extends BaseEntity {

    private Long projectId;

    private String ruleType;

    private String ruleConfig;

    private Boolean enabled;
}
