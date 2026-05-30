package com.aidevhub.service;

import com.aidevhub.common.TaskStatus;
import com.aidevhub.mapper.TaskMapper;
import com.aidevhub.model.Task;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;

    private static final TaskStatus[] FORWARD_PATH = {
            TaskStatus.PENDING,
            TaskStatus.PLANNING,
            TaskStatus.CODING,
            TaskStatus.REVIEWING,
            TaskStatus.PR_OPEN,
            TaskStatus.DEPLOYED
    };

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.DEPLOYED || status == TaskStatus.FAILED;
    }

    @Transactional
    public Task createTask(Task task) {
        task.setStatus(TaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("任务创建成功: id={}, title={}", task.getId(), task.getTitle());
        return task;
    }

    public Task getTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return task;
    }

    public Page<Task> listTasks(Long projectId, Integer page, Integer size) {
        int current = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : size;
        Page<Task> p = new Page<>(current, pageSize);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByDesc(Task::getCreatedAt);
        return taskMapper.selectPage(p, wrapper);
    }

    public Page<Task> pageAll(Integer page, Integer size) {
        int current = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : size;
        Page<Task> p = new Page<>(current, pageSize);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .orderByDesc(Task::getCreatedAt);
        return taskMapper.selectPage(p, wrapper);
    }

    @Transactional
    public Task updateTask(Long id, Task input) {
        Task existing = getTask(id);
        TaskStatus currentStatus = TaskStatus.valueOf(existing.getStatus());
        if (isTerminal(currentStatus)) {
            throw new IllegalStateException("终态任务不可修改，当前状态: " + currentStatus.name());
        }

        if (StringUtils.hasText(input.getTitle())) {
            existing.setTitle(input.getTitle());
        }
        if (StringUtils.hasText(input.getDescription())) {
            existing.setDescription(input.getDescription());
        }
        if (StringUtils.hasText(input.getAcceptance())) {
            existing.setAcceptance(input.getAcceptance());
        }
        if (StringUtils.hasText(input.getPriority())) {
            existing.setPriority(input.getPriority());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(existing);
        log.info("任务更新成功: id={}", id);
        return getTask(id);
    }

    @Transactional
    public void deleteTask(Long id) {
        getTask(id);
        taskMapper.deleteById(id);
        log.info("任务删除成功: id={}", id);
    }

    @Transactional
    public Task updateStatus(Long taskId, TaskStatus newStatus) {
        Task task = getTask(taskId);
        TaskStatus currentStatus = TaskStatus.valueOf(task.getStatus());

        if (isTerminal(currentStatus)) {
            throw new IllegalStateException(
                    "任务已处于终态[" + currentStatus.name() + "]，不能变更状态");
        }

        if (newStatus == TaskStatus.FAILED) {
            task.setStatus(TaskStatus.FAILED.name());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            log.info("任务状态变更: id={}, {} → {}", taskId, currentStatus.name(), TaskStatus.FAILED.name());
            return task;
        }

        boolean validForward = false;
        for (int i = 0; i < FORWARD_PATH.length - 1; i++) {
            if (FORWARD_PATH[i] == currentStatus && FORWARD_PATH[i + 1] == newStatus) {
                validForward = true;
                break;
            }
        }

        if (!validForward) {
            throw new IllegalStateException(
                    "状态不能从 " + currentStatus.name() + " 跳转到 " + newStatus.name());
        }

        task.setStatus(newStatus.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("任务状态变更: id={}, {} → {}", taskId, currentStatus.name(), newStatus.name());
        return task;
    }

    @Transactional
    public Task markFailed(Long taskId) {
        Task task = getTask(taskId);
        TaskStatus currentStatus = TaskStatus.valueOf(task.getStatus());
        if (isTerminal(currentStatus)) {
            throw new IllegalStateException("终态任务不可再变更");
        }
        task.setStatus(TaskStatus.FAILED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("任务标记为失败: id={}", taskId);
        return task;
    }

}
