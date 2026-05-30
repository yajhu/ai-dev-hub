package com.aidevhub.controller;

import com.aidevhub.common.TaskStatus;
import com.aidevhub.mapper.ExecutionMapper;
import com.aidevhub.mapper.ProjectMapper;
import com.aidevhub.mapper.TaskMapper;
import com.aidevhub.model.Execution;
import com.aidevhub.model.Project;
import com.aidevhub.model.Task;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class WebController {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private ExecutionMapper executionMapper;

    @GetMapping("/")
    public String index(Model model) {
        List<Project> projects = projectMapper.selectList(null);
        model.addAttribute("projects", projects);
        return "index";
    }

    @GetMapping("/tasks")
    public String tasksList(@RequestParam(required = false) Long projectId, Model model) {
        List<Task> tasks;
        if (projectId != null) {
            tasks = taskMapper.selectList(
                    new QueryWrapper<Task>().eq("project_id", projectId).orderByDesc("created_at"));
            Project project = projectMapper.selectById(projectId);
            model.addAttribute("project", project);
        } else {
            tasks = taskMapper.selectList(
                    new QueryWrapper<Task>().orderByDesc("created_at"));
        }
        model.addAttribute("tasks", tasks);
        model.addAttribute("projectId", projectId);
        return "tasks";
    }

    @GetMapping("/tasks/create")
    public String createTaskForm(@RequestParam(required = false) Long projectId, Model model) {
        model.addAttribute("projectId", projectId);
        if (projectId != null) {
            Project project = projectMapper.selectById(projectId);
            model.addAttribute("project", project);
        }
        return "create-task";
    }

    @PostMapping("/tasks")
    public String createTask(Task task) {
        task.setStatus(TaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return "redirect:/tasks?projectId=" + task.getProjectId();
    }

    @GetMapping("/tasks/{id}")
    public String taskDetail(@PathVariable Long id, Model model) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("Task not found: " + id);
        }
        model.addAttribute("task", task);

        Project project = projectMapper.selectById(task.getProjectId());
        model.addAttribute("project", project);

        List<Execution> executions = executionMapper.selectList(
                new QueryWrapper<Execution>().eq("task_id", id).orderByDesc("started_at"));
        model.addAttribute("executions", executions);

        String prUrl = null;
        String sonarReport = null;
        for (Execution exec : executions) {
            if (prUrl == null && exec.getPrUrl() != null && !exec.getPrUrl().isEmpty()) {
                prUrl = exec.getPrUrl();
            }
            if (sonarReport == null && exec.getSonarReport() != null && !exec.getSonarReport().isEmpty()) {
                sonarReport = exec.getSonarReport();
            }
        }
        model.addAttribute("prUrl", prUrl);
        model.addAttribute("sonarReport", sonarReport);

        return "task-detail";
    }
}
