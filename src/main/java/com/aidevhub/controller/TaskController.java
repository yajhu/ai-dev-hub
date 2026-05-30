package com.aidevhub.controller;

import com.aidevhub.common.Result;
import com.aidevhub.common.TaskStatus;
import com.aidevhub.common.TaskStatusUpdateRequest;
import com.aidevhub.model.Task;
import com.aidevhub.service.PipelineService;
import com.aidevhub.service.TaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理接口 — REST API for /api/tasks
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final PipelineService pipelineService;

    /**
     * POST /api/tasks — 创建任务
     */
    @PostMapping
    public Result<Task> create(@RequestBody Task task) {
        return Result.ok(taskService.createTask(task));
    }

    /**
     * GET /api/tasks/{id} — 查询单个任务
     */
    @GetMapping("/{id}")
    public Result<Task> getById(@PathVariable Long id) {
        return Result.ok(taskService.getTask(id));
    }

    /**
     * GET /api/tasks?projectId=xx&page=1&size=10 — 按项目分页查询任务列表
     */
    @GetMapping
    public Result<Page<Task>> list(@RequestParam Long projectId,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(taskService.listTasks(projectId, page, size));
    }

    /**
     * PUT /api/tasks/{id} — 更新任务（仅title/description/acceptance/priority）
     */
    @PutMapping("/{id}")
    public Result<Task> update(@PathVariable Long id, @RequestBody Task task) {
        return Result.ok(taskService.updateTask(id, task));
    }

    /**
     * DELETE /api/tasks/{id} — 删除任务
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return Result.ok(null);
    }

    /**
     * PUT /api/tasks/{id}/status — 更新任务状态
     */
    @PutMapping("/{id}/status")
    public Result<Task> updateStatus(@PathVariable Long id, @RequestBody TaskStatusUpdateRequest request) {
        return Result.ok(taskService.updateStatus(id, TaskStatus.valueOf(request.getStatus())));
    }

    /**
     * POST /api/tasks/{id}/execute — 触发任务编排
     */
    @PostMapping("/{id}/execute")
    public Result<String> execute(@PathVariable Long id) {
        pipelineService.executeTask(id);
        return Result.ok("编排已触发");
    }
}
