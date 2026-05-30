package com.aidevhub.service;

import com.aidevhub.common.StageType;
import com.aidevhub.common.TaskStatus;
import com.aidevhub.mapper.ExecutionMapper;
import com.aidevhub.model.Execution;
import com.aidevhub.model.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;

/**
 * 流水线服务 — 执行Python3脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final TaskService taskService;
    private final ExecutionMapper executionMapper;

    /**
     * 执行任务流水线：更新状态到下一阶段 → 创建执行记录 → 调用python3脚本 → 读取输出 → 更新结果
     *
     * @param taskId   任务ID
     * @param scriptPath Python3脚本路径
     */
    @Transactional
    public Execution executeTask(Long taskId, String scriptPath) {
        Task task = taskService.getById(taskId);

        if (task.getStatus().isTerminal()) {
            throw new IllegalStateException("终态任务不可执行");
        }

        // 推进到下一阶段
        TaskStatus currentStatus = task.getStatus();
        TaskStatus nextStatus = null;
        for (int i = 0; i < TaskStatus.values().length - 1; i++) {
            if (TaskStatus.values()[i] == currentStatus) {
                nextStatus = TaskStatus.values()[i + 1];
                break;
            }
        }
        if (nextStatus == null || nextStatus == TaskStatus.FAILED) {
            throw new IllegalStateException("无可用下一阶段，当前状态: " + currentStatus.getDescription());
        }

        taskService.transitionStatus(taskId, nextStatus);

        // 确定阶段类型
        StageType stageType = resolveStageType(nextStatus);

        // 创建执行记录
        Execution execution = new Execution();
        execution.setTaskId(taskId);
        execution.setStageType(stageType);
        execution.setStatus(nextStatus);
        execution.setStartTime(LocalDateTime.now());
        execution.setCreateTime(LocalDateTime.now());
        execution.setUpdateTime(LocalDateTime.now());
        executionMapper.insert(execution);

        // 调用Python3脚本
        try {
            String output = runPythonScript(scriptPath);
            execution.setOutput(output);
            execution.setEndTime(LocalDateTime.now());
            execution.setUpdateTime(LocalDateTime.now());
            executionMapper.updateById(execution);
        } catch (Exception e) {
            log.error("脚本执行失败: taskId={}", taskId, e);
            execution.setStatus(TaskStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.setUpdateTime(LocalDateTime.now());
            executionMapper.updateById(execution);

            taskService.markFailed(taskId, "脚本执行失败: " + e.getMessage());
        }

        return execution;
    }

    /**
     * 根据任务状态解析对应的阶段类型
     */
    private StageType resolveStageType(TaskStatus status) {
        switch (status) {
            case PLANNING: return StageType.PLAN;
            case CODING:   return StageType.CODE;
            case REVIEWING: return StageType.REVIEW;
            case PR_OPEN:  return StageType.TEST;
            case DEPLOYED: return StageType.DEPLOY;
            default:       return StageType.CUSTOM;
        }
    }

    /**
     * 通过Runtime.exec调用python3脚本并读取输出
     */
    private String runPythonScript(String scriptPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("python3", scriptPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("python3脚本退出码: " + exitCode + ", 输出: " + output.toString());
        }

        return output.toString();
    }
}
