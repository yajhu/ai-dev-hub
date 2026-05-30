package com.aidevhub.controller;

import com.aidevhub.common.Result;
import com.aidevhub.mapper.ExecutionMapper;
import com.aidevhub.model.Execution;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 执行记录管理接口
 */
@RestController
@RequestMapping("/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionMapper executionMapper;

    /**
     * 分页查询执行记录列表
     */
    @GetMapping
    public Result<Page<Execution>> page(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) Long taskId) {
        Page<Execution> page = new Page<>(current, size);
        QueryWrapper<Execution> wrapper = new QueryWrapper<Execution>().orderByDesc("create_time");
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        return Result.ok(executionMapper.selectPage(page, wrapper));
    }

    /**
     * 查询单个执行记录
     */
    @GetMapping("/{id}")
    public Result<Execution> getById(@PathVariable Long id) {
        Execution execution = executionMapper.selectById(id);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在: " + id);
        }
        return Result.ok(execution);
    }
}
