package com.aidevhub.controller;

import com.aidevhub.common.BusinessException;
import com.aidevhub.common.Result;
import com.aidevhub.mapper.ProjectMapper;
import com.aidevhub.model.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private ProjectMapper projectMapper;

    @PostMapping
    public Result<Project> createProject(@RequestBody Project project) {
        projectMapper.insert(project);
        return Result.ok(project);
    }

    @GetMapping
    public Result<List<Project>> listProjects() {
        List<Project> projects = projectMapper.selectList(null);
        return Result.ok(projects);
    }

    @GetMapping("/{id}")
    public Result<Project> getProject(@PathVariable Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "Project not found: " + id);
        }
        return Result.ok(project);
    }
}
