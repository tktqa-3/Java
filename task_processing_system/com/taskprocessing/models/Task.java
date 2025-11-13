// models/Task.java
//
// 【処理概要】
// 実行タスクを表すモデルクラス。
// タスクのメタデータと実行ロジックを保持する。

package com.taskprocessing.models;

import java.util.*;
import java.util.concurrent.Callable;

public class Task {
    private final String id;
    private final String description;
    private final Date createdAt;
    private TaskPriority priority;
    private int maxRetries;
    private TaskExecutor executor;
    private final Map<String, String> metadata;
    
    public Task(String id, String description) {
        this.id = id;
        this.description = description;
        this.createdAt = new Date();
        this.priority = TaskPriority.NORMAL;
        this.maxRetries = 0;
        this.metadata = new HashMap<>();
    }
    
    // Getters
    public String getId() { return id; }
    public String getDescription() { return description; }
    public Date getCreatedAt() { return createdAt; }
    public TaskPriority getPriority() { return priority; }
    public int getMaxRetries() { return maxRetries; }
    public TaskExecutor getExecutor() { return executor; }
    public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
    
    // Setters
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public void setExecutor(TaskExecutor executor) { this.executor = executor; }
    
    // Metadata操作
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
    
    public String getMetadata(String key) {
        return metadata.get(key);
    }
    
    @Override
    public String toString() {
        return String.format("Task[id=%s, priority=%s, description=%s]", 
            id, priority, description);
    }
}

// ===== TaskExecutor =====

@FunctionalInterface
interface TaskExecutor {
    TaskResult execute() throws Exception;
}

// ===== TaskPriority =====

enum TaskPriority {
    LOW(1),
    NORMAL(5),
    HIGH(8),
    URGENT(10);
    
    private final int value;
    
    TaskPriority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
}

// ===== TaskStatus =====

enum TaskStatus {
    QUEUED,      // キュー待ち
    RUNNING,     // 実行中
    COMPLETED,   // 完了
    FAILED,      // 失敗
    CANCELLED,   // キャンセル
    UNKNOWN      // 不明
}

// ===== TaskResult =====

class TaskResult {
    private final String taskId;
    private final boolean success;
    private final String message;
    private final Date completedAt;
    private final Map<String, Object> data;
    
    public TaskResult(String taskId, boolean success, String message) {
        this.taskId = taskId;
        this.success = success;
        this.message = message;
        this.completedAt = new Date();
        this.data = new HashMap<>();
    }
    
    public String getTaskId() { return taskId; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Date getCompletedAt() { return completedAt; }
    public Map<String, Object> getData() { return new HashMap<>(data); }
    
    public void addData(String key, Object value) {
        data.put(key, value);
    }
    
    @Override
    public String toString() {
        return String.format("TaskResult[taskId=%s, success=%s, message=%s]",
            taskId, success, message);
    }
}

// ===== TaskEvent =====

class TaskEvent {
    private final TaskEventType type;
    private final String taskId;
    private final Date timestamp;
    private final Map<String, String> data;
    
    public TaskEvent(TaskEventType type, String taskId, Map<String, String> data) {
        this.type = type;
        this.taskId = taskId;
        this.timestamp = new Date();
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
    }
    
    public TaskEventType getType() { return type; }
    public String getTaskId() { return taskId; }
    public Date getTimestamp() { return timestamp; }
    public Map<String, String> getData() { return new HashMap<>(data); }
    
    @Override
    public String toString() {
        return String.format("TaskEvent[type=%s, taskId=%s, timestamp=%s]",
            type, taskId, timestamp);
    }
}

// ===== TaskEventType =====

enum TaskEventType {
    TASK_STARTED,    // タスク開始
    TASK_COMPLETED,  // タスク完了
    TASK_FAILED,     // タスク失敗
    TASK_RETRY,      // リトライ
    TASK_CANCELLED   // キャンセル
}
