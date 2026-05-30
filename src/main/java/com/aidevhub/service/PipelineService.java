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

    /** Python编排脚本固定路径 */
    private static final String PIPELINE_SCRIPT = "/home/huyajun/workspace/ai-dev-hub/scripts/pipeline.py";

    /**
     * 执行任务编排：更新状态为PLANNING → 创建执行记录 → 调用python3脚本 → 读取输出 → 更新结果
     *
     * @param taskId 任务ID
     * @return 执行记录
     */
    @Transactional
    public Execution executeTask(Long taskId) {
        Task task = taskService.getTask(taskId);

        // 推进到 PLANNING 状态
        taskService.updateStatus(taskId, TaskStatus.PLANNING);

        // 创建执行记录
        Execution execution = new Execution();
        execution.setTaskId(taskId);
        execution.setStage(StageType.PLAN.name());
        execution.setStatus("RUNNING");
        execution.setStartedAt(LocalDateTime.now());
        execution.setCreatedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.insert(execution);

        // 调用Python3脚本
        try {
            String output = runPythonScript(taskId);
            execution.setOutput(output);
            execution.setFinishedAt(LocalDateTime.now());
            execution.setStatus("SUCCESS");
            execution.setUpdatedAt(LocalDateTime.now());
            executionMapper.updateById(execution);

            // 成功后推进到 CODING
            taskService.updateStatus(taskId, TaskStatus.CODING);
        } catch (Exception e) {
            log.error("脚本执行失败: taskId={}", taskId, e);
            execution.setStatus("FAILED");
            execution.setOutput(e.getMessage());
            execution.setFinishedAt(LocalDateTime.now());
            execution.setUpdatedAt(LocalDateTime.now());
            executionMapper.updateById(execution);

            taskService.markFailed(taskId);
        }

        return execution;
    }

    /**
     * 通过Runtime.exec调用python3脚本并读取stdout和stderr输出
     */
    private String runPythonScript(Long taskId) throws Exception {
        Process process = Runtime.getRuntime().exec(
                new String[]{"python3", PIPELINE_SCRIPT, String.valueOf(taskId)});

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        // 读取 stdout
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line).append("\n");
            }
        }

        // 读取 stderr
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stderr.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Python脚本执行超时（5分钟）");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Python脚本退出码: " + exitCode
                    + ", stderr: " + stderr.toString().trim());
        }

        log.info("脚本执行成功: taskId={}, output长度={}", taskId, stdout.length());
        return stdout.toString();
    }
}
