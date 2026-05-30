package com.aidevhub.model;

import com.aidevhub.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseEntity {

    private String name;
    private String repoUrl;
    private String techStack;
    private String sonarKey;
}
