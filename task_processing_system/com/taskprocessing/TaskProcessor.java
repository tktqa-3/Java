// TaskProcessor.java
//
// 【処理概要】
// タスクの非同期実行を管理するプロセッサ。
// ExecutorServiceとBlockingQueueを使用して、
// 複数のタスクを並行処理する。
//
// 【主な機能】
// - マルチスレッドでのタスク並行実行
// - 優先度付きタスクキュー
// - 自動リトライ機構
// - タスク状態の追跡
// - イベント通知
// - 統計情報の収集
//
// 【実装内容】
// 1. ExecutorServiceによるスレッドプール管理
// 2. 優先度付きキューからのタスク取得
// 3. タスク実行とエラーハンドリング
// 4. リトライロジック（指数バックオフ）
// 5. イベント発行
// 6. 統計情報の集計

package com.taskprocessing;

import com.taskprocessing.models.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class TaskProcessor {
    
    private final ExecutorService executorService;
    private final TaskQueue taskQueue;
    private final EventBus eventBus;
    
    // 統計情報（スレッドセーフ）
    private final AtomicInteger totalTasks = new AtomicInteger(0);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final AtomicInteger failedTasks = new AtomicInteger(0);
    private final AtomicInteger retriedTasks = new AtomicInteger(0);
    
    // タスク状態管理
    private final Map<String, TaskStatus> taskStatuses = new ConcurrentHashMap<>();
    
    /**
     * コンストラクタ
     * 
     * @param threadPoolSize スレッドプール数
     */
    public TaskProcessor(int threadPoolSize) {
        this.executorService = Executors.newFixedThreadPool(
            threadPoolSize,
            new ThreadFactory() {
                private final AtomicInteger threadCount = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("TaskWorker-" + threadCount.getAndIncrement());
                    thread.setDaemon(false);
                    return thread;
                }
            }
        );
        
        this.taskQueue = new TaskQueue();
        this.eventBus = EventBus.getInstance();
    }
    
    /**
     * タスクを投入して非同期実行
     * 
     * @param task 実行するタスク
     * @return 実行結果のFuture
     */
    public Future<TaskResult> submitTask(Task task) {
        totalTasks.incrementAndGet();
        taskStatuses.put(task.getId(), TaskStatus.QUEUED);
        
        // タスクをキューに追加
        taskQueue.enqueue(task);
        
        // 非同期実行
        return executorService.submit(() -> executeTask(task));
    }
    
    /**
     * タスクを実行（リトライ機構付き）
     * 
     * @param task 実行するタスク
     * @return 実行結果
     */
    private TaskResult executeTask(Task task) {
        int currentRetry = 0;
        Exception lastException = null;
        
        taskStatuses.put(task.getId(), TaskStatus.RUNNING);
        
        // タスク開始イベント発行
        publishTaskEvent(
            TaskEventType.TASK_STARTED,
            task,
            Map.of(
                "description", task.getDescription(),
                "priority", task.getPriority().toString()
            )
        );
        
        while (currentRetry <= task.getMaxRetries()) {
            try {
                // タスク実行
                TaskResult result = task.getExecutor().execute();
                
                // 成功
                taskStatuses.put(task.getId(), TaskStatus.COMPLETED);
                completedTasks.incrementAndGet();
                
                // 完了イベント発行
                publishTaskEvent(
                    TaskEventType.TASK_COMPLETED,
                    task,
                    Map.of(
                        "message", result.getMessage(),
                        "retryCount", String.valueOf(currentRetry)
                    )
                );
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                // 最大リトライ回数に達していない場合はリトライ
                if (currentRetry < task.getMaxRetries()) {
                    currentRetry++;
                    retriedTasks.incrementAndGet();
                    
                    // リトライイベント発行
                    publishTaskEvent(
                        TaskEventType.TASK_RETRY,
                        task,
                        Map.of(
                            "currentRetry", String.valueOf(currentRetry),
                            "maxRetries", String.valueOf(task.getMaxRetries()),
                            "error", e.getMessage()
                        )
                    );
                    
                    // 指数バックオフ（exponential backoff）
                    long backoffTime = (long) (Math.pow(2, currentRetry - 1) * 1000);
                    backoffTime = Math.min(backoffTime, 10000); // 最大10秒
                    
                    try {
                        Thread.sleep(backoffTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    // 最大リトライ回数に達した
                    break;
                }
            }
        }
        
        // 失敗
        taskStatuses.put(task.getId(), TaskStatus.FAILED);
        failedTasks.incrementAndGet();
        
        // 失敗イベント発行
        publishTaskEvent(
            TaskEventType.TASK_FAILED,
            task,
            Map.of(
                "error", lastException != null ? lastException.getMessage() : "Unknown error",
                "retryCount", String.valueOf(currentRetry)
            )
        );
        
        return new TaskResult(
            task.getId(),
            false,
            "タスク失敗: " + (lastException != null ? lastException.getMessage() : "Unknown error")
        );
    }
    
    /**
     * タスクイベントを発行
     * 
     * @param type イベントタイプ
     * @param task タスク
     * @param data 追加データ
     */
    private void publishTaskEvent(
        TaskEventType type,
        Task task,
        Map<String, String> data
    ) {
        TaskEvent event = new TaskEvent(
            type,
            task.getId(),
            new HashMap<>(data)
        );
        eventBus.publish(event);
    }
    
    /**
     * 特定タスクの状態を取得
     * 
     * @param taskId タスクID
     * @return タスク状態
     */
    public TaskStatus getTaskStatus(String taskId) {
        return taskStatuses.getOrDefault(taskId, TaskStatus.UNKNOWN);
    }
    
    /**
     * 統計情報を表示
     */
    public void printStatistics() {
        System.out.println("  総タスク数: " + totalTasks.get());
        System.out.println("  完了: " + completedTasks.get());
        System.out.println("  失敗: " + failedTasks.get());
        System.out.println("  リトライ総数: " + retriedTasks.get());
        
        if (totalTasks.get() > 0) {
            double successRate = (completedTasks.get() * 100.0) / totalTasks.get();
            System.out.println(String.format("  成功率: %.1f%%", successRate));
        }
        
        // 状態別カウント
        Map<TaskStatus, Long> statusCounts = taskStatuses.values().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                status -> status,
                java.util.stream.Collectors.counting()
            ));
        
        System.out.println("\n  状態別:");
        statusCounts.forEach((status, count) -> {
            System.out.println(String.format("    %s: %d", status, count));
        });
    }
    
    /**
     * 統計情報を取得
     * 
     * @return 統計情報マップ
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", totalTasks.get());
        stats.put("completed", completedTasks.get());
        stats.put("failed", failedTasks.get());
        stats.put("retried", retriedTasks.get());
        return stats;
    }
    
    /**
     * プロセッサをシャットダウン
     */
    public void shutdown() {
        executorService.shutdown();
        
        try {
            // 最大30秒待機
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                
                // さらに10秒待機
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("⚠️  ExecutorServiceが終了しませんでした");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * プロセッサがシャットダウンされているか確認
     * 
     * @return シャットダウン済みならtrue
     */
    public boolean isShutdown() {
        return executorService.isShutdown();
    }
    
    /**
     * 全タスクが終了しているか確認
     * 
     * @return 終了済みならtrue
     */
    public boolean isTerminated() {
        return executorService.isTerminated();
    }
}
