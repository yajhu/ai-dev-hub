package com.aidevhub.controller;

import com.aidevhub.common.BusinessException;
import com.aidevhub.common.Result;
import com.aidevhub.common.TaskStatus;
import com.aidevhub.model.Task;
import com.aidevhub.service.PipelineService;
import com.aidevhub.service.TaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private PipelineService pipelineService;

    @PostMapping
    public Result<Task> createTask(@RequestBody Task task) {
        Task created = taskService.createTask(task);
        return Result.ok(created);
    }

    @GetMapping
    public Result<Page<Task>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Task> result = taskService.listTasks(status, page, size);
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<Task> getTask(@PathVariable Long id) {
        Task task = taskService.getTask(id);
        return Result.ok(task);
    }

    @PutMapping("/{id}/status")
    public Result<Task> updateTaskStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isEmpty()) {
            throw new BusinessException(400, "status is required");
        }
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Invalid status: " + statusStr);
        }
        Task updated = taskService.updateTaskStatus(id, newStatus);
        return Result.ok(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return Result.ok();
    }

    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> executeTask(@PathVariable Long id) {
        return pipelineService.executeTask(id);
    }
}
