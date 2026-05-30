package com.aidevhub.controller;

import com.aidevhub.common.Result;
import com.aidevhub.mapper.ProjectMapper;
import com.aidevhub.model.Project;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 项目管理接口
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;

    /**
     * 创建项目
     */
    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.insert(project);
        return Result.ok(project);
    }

    /**
     * 查询单个项目
     */
    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + id);
        }
        return Result.ok(project);
    }

    /**
     * 分页查询项目列表
     */
    @GetMapping
    public Result<Page<Project>> page(@RequestParam(defaultValue = "1") int current,
                                      @RequestParam(defaultValue = "10") int size) {
        Page<Project> page = new Page<>(current, size);
        return Result.ok(projectMapper.selectPage(page, new QueryWrapper<Project>().orderByDesc("create_time")));
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
        return Result.ok(projectMapper.selectById(id));
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectMapper.deleteById(id);
        return Result.ok(null);
    }
}
