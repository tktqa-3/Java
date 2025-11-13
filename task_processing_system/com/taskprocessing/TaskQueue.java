// TaskQueue.java
//
// 【処理概要】
// 優先度付きタスクキューの実装。
// PriorityBlockingQueueを使用して、
// 優先度の高いタスクから処理される。
//
// 【主な機能】
// - 優先度に基づくタスクのソート
// - スレッドセーフなエンキュー/デキュー
// - ブロッキング操作（タスクがない場合は待機）
// - キューサイズの追跡
//
// 【実装内容】
// 1. PriorityBlockingQueueでの優先度管理
// 2. Comparatorによる優先度比較
// 3. タイムスタンプによる同一優先度の順序保証
// 4. スレッドセーフな操作
// 5. キュー統計の提供

package com.taskprocessing;

import com.taskprocessing.models.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class TaskQueue {
    
    // 優先度付きブロッキングキュー
    private final PriorityBlockingQueue<Task> queue;
    
    // 統計情報
    private final AtomicInteger enqueuedCount = new AtomicInteger(0);
    private final AtomicInteger dequeuedCount = new AtomicInteger(0);
    
    /**
     * コンストラクタ
     * 
     * 優先度に基づいてタスクをソートするComparatorを設定
     */
    public TaskQueue() {
        this.queue = new PriorityBlockingQueue<>(
            100, // 初期容量
            (task1, task2) -> {
                // 優先度の比較（降順：高い優先度が先）
                int priorityCompare = task2.getPriority().getValue() 
                                    - task1.getPriority().getValue();
                
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                
                // 優先度が同じ場合はタイムスタンプで比較（FIFO）
                return Long.compare(
                    task1.getCreatedAt().getTime(),
                    task2.getCreatedAt().getTime()
                );
            }
        );
    }
    
    /**
     * タスクをキューに追加
     * 
     * @param task 追加するタスク
     */
    public void enqueue(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        queue.offer(task);
        enqueuedCount.incrementAndGet();
    }
    
    /**
     * キューからタスクを取得（ブロッキング）
     * 
     * キューが空の場合、タスクが追加されるまで待機する
     * 
     * @return 取得したタスク
     * @throws InterruptedException 待機中に割り込まれた場合
     */
    public Task dequeue() throws InterruptedException {
        Task task = queue.take();
        dequeuedCount.incrementAndGet();
        return task;
    }
    
    /**
     * キューからタスクを取得（タイムアウト付き）
     * 
     * @param timeout タイムアウト時間
     * @param unit 時間単位
     * @return 取得したタスク（タイムアウトした場合はnull）
     * @throws InterruptedException 待機中に割り込まれた場合
     */
    public Task dequeue(long timeout, TimeUnit unit) throws InterruptedException {
        Task task = queue.poll(timeout, unit);
        
        if (task != null) {
            dequeuedCount.incrementAndGet();
        }
        
        return task;
    }
    
    /**
     * キューからタスクを取得（非ブロッキング）
     * 
     * @return 取得したタスク（キューが空の場合はnull）
     */
    public Task poll() {
        Task task = queue.poll();
        
        if (task != null) {
            dequeuedCount.incrementAndGet();
        }
        
        return task;
    }
    
    /**
     * キューの先頭タスクを参照（削除しない）
     * 
     * @return 先頭のタスク（キューが空の場合はnull）
     */
    public Task peek() {
        return queue.peek();
    }
    
    /**
     * キューのサイズを取得
     * 
     * @return 現在のキューサイズ
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * キューが空かチェック
     * 
     * @return 空ならtrue
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * キューをクリア
     */
    public void clear() {
        queue.clear();
    }
    
    /**
     * エンキューされた総タスク数を取得
     * 
     * @return エンキュー総数
     */
    public int getEnqueuedCount() {
        return enqueuedCount.get();
    }
    
    /**
     * デキューされた総タスク数を取得
     * 
     * @return デキュー総数
     */
    public int getDequeuedCount() {
        return dequeuedCount.get();
    }
    
    /**
     * キュー統計情報を表示
     */
    public void printStatistics() {
        System.out.println("タスクキュー統計:");
        System.out.println("  現在のサイズ: " + size());
        System.out.println("  エンキュー総数: " + enqueuedCount.get());
        System.out.println("  デキュー総数: " + dequeuedCount.get());
        System.out.println("  待機中: " + (enqueuedCount.get() - dequeuedCount.get()));
    }
}
