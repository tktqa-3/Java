// EventBus.java
//
// 【処理概要】
// イベント駆動アーキテクチャのためのイベントバス実装。
// Pub/Sub（発行/購読）パターンで、
// イベントの発行と購読を管理する。
//
// 【主な機能】
// - イベントの発行（publish）
// - イベントの購読（subscribe）
// - リスナーの登録/解除
// - スレッドセーフな操作
// - 非同期イベント配信
//
// 【実装内容】
// 1. シングルトンパターンでのインスタンス管理
// 2. イベントタイプごとのリスナーリスト管理
// 3. ConcurrentHashMapでのスレッドセーフな実装
// 4. 非同期イベント配信（別スレッドで実行）
// 5. エラーハンドリング

package com.taskprocessing;

import com.taskprocessing.models.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EventBus {
    
    // シングルトンインスタンス
    private static final EventBus INSTANCE = new EventBus();
    
    // イベントタイプごとのリスナーリスト
    private final Map<TaskEventType, List<Consumer<TaskEvent>>> listeners;
    
    // イベント配信用のExecutorService
    private final ExecutorService eventExecutor;
    
    /**
     * プライベートコンストラクタ（シングルトン）
     */
    private EventBus() {
        this.listeners = new ConcurrentHashMap<>();
        this.eventExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("EventBus-Worker");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    /**
     * シングルトンインスタンスを取得
     * 
     * @return EventBusインスタンス
     */
    public static EventBus getInstance() {
        return INSTANCE;
    }
    
    /**
     * イベントリスナーを登録
     * 
     * @param eventType イベントタイプ
     * @param listener リスナー
     */
    public void subscribe(TaskEventType eventType, Consumer<TaskEvent> listener) {
        if (eventType == null || listener == null) {
            throw new IllegalArgumentException("Event type and listener cannot be null");
        }
        
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }
    
    /**
     * イベントリスナーを解除
     * 
     * @param eventType イベントタイプ
     * @param listener リスナー
     */
    public void unsubscribe(TaskEventType eventType, Consumer<TaskEvent> listener) {
        if (eventType == null || listener == null) {
            return;
        }
        
        List<Consumer<TaskEvent>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }
    
    /**
     * 特定イベントタイプの全リスナーを解除
     * 
     * @param eventType イベントタイプ
     */
    public void unsubscribeAll(TaskEventType eventType) {
        listeners.remove(eventType);
    }
    
    /**
     * 全リスナーを解除
     */
    public void clear() {
        listeners.clear();
    }
    
    /**
     * イベントを発行（非同期）
     * 
     * 登録されている全リスナーに対して、
     * 別スレッドでイベントを配信する
     * 
     * @param event 発行するイベント
     */
    public void publish(TaskEvent event) {
        if (event == null) {
            return;
        }
        
        List<Consumer<TaskEvent>> eventListeners = listeners.get(event.getType());
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            return;
        }
        
        // 非同期でリスナーを実行
        eventExecutor.submit(() -> {
            for (Consumer<TaskEvent> listener : eventListeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    // リスナーのエラーがイベントバス全体に影響しないようにする
                    System.err.println("⚠️  イベントリスナーでエラー: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * イベントを発行（同期）
     * 
     * 現在のスレッドで全リスナーを実行する
     * 
     * @param event 発行するイベント
     */
    public void publishSync(TaskEvent event) {
        if (event == null) {
            return;
        }
        
        List<Consumer<TaskEvent>> eventListeners = listeners.get(event.getType());
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            return;
        }
        
        for (Consumer<TaskEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                System.err.println("⚠️  イベントリスナーでエラー: " + e.getMessage());
            }
        }
    }
    
    /**
     * 登録されているリスナー数を取得
     * 
     * @param eventType イベントタイプ
     * @return リスナー数
     */
    public int getListenerCount(TaskEventType eventType) {
        List<Consumer<TaskEvent>> eventListeners = listeners.get(eventType);
        return eventListeners != null ? eventListeners.size() : 0;
    }
    
    /**
     * 全イベントタイプの総リスナー数を取得
     * 
     * @return 総リスナー数
     */
    public int getTotalListenerCount() {
        return listeners.values().stream()
                        .mapToInt(List::size)
                        .sum();
    }
    
    /**
     * 登録されているイベントタイプの一覧を取得
     * 
     * @return イベントタイプのセット
     */
    public Set<TaskEventType> getEventTypes() {
        return new HashSet<>(listeners.keySet());
    }
    
    /**
     * EventBusをシャットダウン
     */
    public void shutdown() {
        eventExecutor.shutdown();
        
        try {
            if (!eventExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                eventExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            eventExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * EventBus統計情報を表示
     */
    public void printStatistics() {
        System.out.println("EventBus統計:");
        System.out.println("  登録イベントタイプ数: " + listeners.size());
        System.out.println("  総リスナー数: " + getTotalListenerCount());
        
        System.out.println("\n  イベントタイプ別:");
        listeners.forEach((type, listenerList) -> {
            System.out.println(String.format("    %s: %d リスナー", 
                type, listenerList.size()));
        });
    }
}
