package com.aidevhub.service;

import com.aidevhub.common.BusinessException;
import com.aidevhub.common.TaskStatus;
import com.aidevhub.mapper.ExecutionMapper;
import com.aidevhub.mapper.TaskMapper;
import com.aidevhub.model.Task;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class TaskService {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private ExecutionMapper executionMapper;

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(TaskStatus.PENDING, EnumSet.of(TaskStatus.PLANNING));
        ALLOWED_TRANSITIONS.put(TaskStatus.PLANNING, EnumSet.of(TaskStatus.CODING));
        ALLOWED_TRANSITIONS.put(TaskStatus.CODING, EnumSet.of(TaskStatus.REVIEWING));
        ALLOWED_TRANSITIONS.put(TaskStatus.REVIEWING, EnumSet.of(TaskStatus.PR_OPEN, TaskStatus.CODING));
        ALLOWED_TRANSITIONS.put(TaskStatus.PR_OPEN, EnumSet.of(TaskStatus.DEPLOYED));
        ALLOWED_TRANSITIONS.put(TaskStatus.DEPLOYED, EnumSet.noneOf(TaskStatus.class));
        ALLOWED_TRANSITIONS.put(TaskStatus.FAILED, EnumSet.noneOf(TaskStatus.class));
    }

    public Task createTask(Task task) {
        task.setStatus(TaskStatus.PENDING.name());
        taskMapper.insert(task);
        return task;
    }

    public Task getTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + id);
        }
        return task;
    }

    public Page<Task> listTasks(String status, int page, int size) {
        Page<Task> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(Task::getStatus, status);
        }
        wrapper.orderByDesc(Task::getCreatedAt);
        return taskMapper.selectPage(pageParam, wrapper);
    }

    public Task updateTaskStatus(Long taskId, TaskStatus newStatus) {
        Task task = getTask(taskId);
        TaskStatus currentStatus;
        try {
            currentStatus = TaskStatus.valueOf(task.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Unknown task status: " + task.getStatus());
        }

        if (currentStatus == newStatus) {
            return task;
        }

        Set<TaskStatus> allowed = ALLOWED_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            if (newStatus == TaskStatus.FAILED) {
                task.setStatus(TaskStatus.FAILED.name());
                taskMapper.updateById(task);
                return task;
            }
            throw new BusinessException(400,
                    "Invalid status transition: " + currentStatus + " -> " + newStatus);
        }

        task.setStatus(newStatus.name());
        taskMapper.updateById(task);
        return task;
    }

    public void deleteTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + id);
        }
        taskMapper.deleteById(id);
    }
}
