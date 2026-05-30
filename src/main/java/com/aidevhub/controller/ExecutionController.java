package com.aidevhub.controller;

import com.aidevhub.common.Result;
import com.aidevhub.mapper.ExecutionMapper;
import com.aidevhub.model.Execution;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/executions")
public class ExecutionController {

    @Autowired
    private ExecutionMapper executionMapper;

    @GetMapping
    public Result<List<Execution>> listExecutions(@RequestParam(required = false) Long taskId) {
        LambdaQueryWrapper<Execution> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(Execution::getTaskId, taskId);
        }
        wrapper.orderByDesc(Execution::getCreatedAt);
        List<Execution> executions = executionMapper.selectList(wrapper);
        return Result.ok(executions);
    }
}
