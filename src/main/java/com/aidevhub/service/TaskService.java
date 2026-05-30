package com.aidevhub.service;

import com.aidevhub.common.TaskStatus;
import com.aidevhub.mapper.TaskMapper;
import com.aidevhub.model.Task;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 任务服务 — CRUD + 严格状态机
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;

    /** 允许的状态转换表：PENDING→PLANNING→CODING→REVIEWING→PR_OPEN→DEPLOYED */
    private static final TaskStatus[] FORWARD_PATH = {
        TaskStatus.PENDING, TaskStatus.PLANNING, TaskStatus.CODING,
        TaskStatus.REVIEWING, TaskStatus.PR_OPEN, TaskStatus.DEPLOYED
    };

    /**
     * 创建任务，初始状态为PENDING
     */
    @Transactional
    public Task create(Task task) {
        task.setStatus(TaskStatus.PENDING);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    /**
     * 根据ID查询任务
     */
    public Task getById(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return task;
    }

    /**
     * 分页查询任务列表
     */
    public Page<Task> page(int current, int size) {
        Page<Task> page = new Page<>(current, size);
        return taskMapper.selectPage(page, new QueryWrapper<Task>().orderByDesc("create_time"));
    }

    /**
     * 更新任务基本信息
     */
    @Transactional
    public Task update(Task task) {
        Task existing = getById(task.getId());
        if (existing.getStatus().isTerminal()) {
            throw new IllegalStateException("终态任务不可修改");
        }
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return getById(task.getId());
    }

    /**
     * 删除任务
     */
    @Transactional
    public void delete(Long id) {
        Task task = getById(id);
        if (task.getStatus().isTerminal()) {
            throw new IllegalStateException("终态任务不可删除");
        }
        taskMapper.deleteById(id);
    }

    /**
     * 严格状态机转换：PENDING→PLANNING→CODING→REVIEWING→PR_OPEN→DEPLOYED，
     * FAILED可从任意非终态转入，终态(DEPLOYED/FAILED)不可再变更
     */
    @Transactional
    public Task transitionStatus(Long taskId, TaskStatus targetStatus) {
        Task task = getById(taskId);
        TaskStatus current = task.getStatus();

        if (current.isTerminal()) {
            throw new IllegalStateException(
                "当前状态[" + current.getDescription() + "]为终态，不可再变更");
        }

        if (!current.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                "不允许从[" + current.getDescription() + "]转换到[" + targetStatus.getDescription() + "]，"
                + "只能按序推进或转入失败状态");
        }

        task.setStatus(targetStatus);
        task.setUpdateTime(LocalDateTime.now());
        if (targetStatus == TaskStatus.FAILED) {
            task.setErrorMessage("任务进入失败状态");
        }
        taskMapper.updateById(task);
        return task;
    }

    /**
     * 将任务标记为失败
     */
    @Transactional
    public Task markFailed(Long taskId, String errorMessage) {
        Task task = getById(taskId);
        if (task.getStatus().isTerminal()) {
            throw new IllegalStateException("终态任务不可再变更");
        }
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }
}
