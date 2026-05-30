package com.aidevhub.controller;

import com.aidevhub.common.Result;
import com.aidevhub.model.Task;
import com.aidevhub.service.PipelineService;
import com.aidevhub.service.TaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理接口
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final PipelineService pipelineService;

    /**
     * 创建任务
     */
    @PostMapping
    public Result<Task> create(@RequestBody Task task) {
        return Result.ok(taskService.create(task));
    }

    /**
     * 查询单个任务
     */
    @GetMapping("/{id}")
    public Result<Task> getById(@PathVariable Long id) {
        return Result.ok(taskService.getById(id));
    }

    /**
     * 分页查询任务列表
     */
    @GetMapping
    public Result<Page<Task>> page(@RequestParam(defaultValue = "1") int current,
                                   @RequestParam(defaultValue = "10") int size) {
        return Result.ok(taskService.page(current, size));
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    public Result<Task> update(@PathVariable Long id, @RequestBody Task task) {
        task.setId(id);
        return Result.ok(taskService.update(task));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.ok(null);
    }

    /**
     * 执行任务流水线
     */
    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id, @RequestParam String scriptPath) {
        pipelineService.executeTask(id, scriptPath);
        return Result.ok(null);
    }
}
