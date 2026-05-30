package com.aidevhub.service;

import com.aidevhub.common.BusinessException;
import com.aidevhub.common.Result;
import com.aidevhub.mapper.TaskMapper;
import com.aidevhub.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    @Autowired
    private TaskMapper taskMapper;

    @Value("${DB_HOST:localhost}")
    private String dbHost;

    @Value("${DB_PORT:5432}")
    private String dbPort;

    @Value("${DB_NAME:aidevhub}")
    private String dbName;

    @Value("${DB_USER:aidevhub}")
    private String dbUser;

    @Value("${DB_PASSWORD:aidevhub}")
    private String dbPassword;

    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> executeTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "Task not found: " + taskId);
        }

        String cmd = "python3 orchestrator/pipeline.py --task-id " + taskId;
        log.info("Executing pipeline: {}", cmd);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", "orchestrator/pipeline.py", "--task-id", taskId.toString());
            pb.environment().put("DB_HOST", dbHost);
            pb.environment().put("DB_PORT", dbPort);
            pb.environment().put("DB_NAME", dbName);
            pb.environment().put("DB_USER", dbUser);
            pb.environment().put("DB_PASSWORD", dbPassword);

            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            log.info("Pipeline exit code: {}", exitCode);

            if (exitCode != 0) {
                log.error("Pipeline stderr: {}", stderr);
                throw new BusinessException(500, "Pipeline failed with exit code " + exitCode
                        + ": " + stderr.toString().trim());
            }

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> result = objectMapper.readValue(
                    stdout.toString().trim(), Map.class);

            return Result.ok(result);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pipeline execution error", e);
            throw new BusinessException(500, "Pipeline execution failed: " + e.getMessage());
        }
    }
}
