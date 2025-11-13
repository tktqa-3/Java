// Main.java
//
// 【処理概要】
// 非同期タスク処理システムのエントリーポイント。
// ECサイトの注文処理をシミュレートし、マルチスレッドで並行実行する。
//
// 【主な機能】
// - タスクプロセッサの初期化
// - イベントリスナーの登録
// - サンプル注文タスクの投入
// - 処理結果の集計と表示
//
// 【実装内容】
// 1. システムコンポーネントの初期化
// 2. イベントリスナーの設定（ログ出力、通知等）
// 3. 複数の注文タスクを並行投入
// 4. 全タスクの完了待機
// 5. 統計情報の表示とシャットダウン

package com.taskprocessing;

import com.taskprocessing.models.*;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   非同期タスク処理システム - EC注文処理デモ           ║");
        System.out.println("║   Multi-threaded Task Processing System                ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        // システム初期化
        TaskProcessor processor = new TaskProcessor(4); // 4ワーカースレッド
        EventBus eventBus = EventBus.getInstance();
        
        // イベントリスナーの登録
        setupEventListeners(eventBus);
        
        System.out.println("🚀 システム起動完了\n");
        System.out.println("📊 ワーカースレッド数: 4");
        System.out.println("⚙️  リトライ機能: 有効");
        System.out.println("🔔 イベント通知: 有効\n");
        System.out.println("=" .repeat(60) + "\n");
        
        // サンプル注文を投入
        List<Future<TaskResult>> futures = new ArrayList<>();
        
        // 注文1: 通常の成功パターン
        futures.add(processor.submitTask(createOrderTask(
            "ORD-001", 
            "ノートPC", 
            1, 
            150000,
            TaskPriority.HIGH
        )));
        
        // 注文2: 在庫不足（リトライで成功するパターン）
        futures.add(processor.submitTask(createOrderTask(
            "ORD-002", 
            "マウス", 
            100, 
            2000,
            TaskPriority.NORMAL
        )));
        
        // 注文3: 高優先度タスク
        futures.add(processor.submitTask(createOrderTask(
            "ORD-003", 
            "キーボード", 
            2, 
            8000,
            TaskPriority.URGENT
        )));
        
        // 注文4: 通常優先度
        futures.add(processor.submitTask(createOrderTask(
            "ORD-004", 
            "モニター", 
            1, 
            30000,
            TaskPriority.NORMAL
        )));
        
        // 注文5: 決済エラーパターン（リトライ）
        futures.add(processor.submitTask(createOrderTask(
            "ORD-005", 
            "Webカメラ", 
            1, 
            5000,
            TaskPriority.LOW
        )));
        
        System.out.println("📦 5件の注文を投入しました\n");
        
        // 全タスクの完了を待機
        waitForAllTasks(futures);
        
        // 統計情報を表示
        System.out.println("\n" + "=" .repeat(60));
        System.out.println("\n📊 処理統計\n");
        processor.printStatistics();
        
        // システムシャットダウン
        System.out.println("\n🛑 システムをシャットダウンしています...");
        processor.shutdown();
        
        System.out.println("✅ 全処理が完了しました");
    }
    
    /**
     * 注文処理タスクを作成
     * 
     * @param orderId 注文ID
     * @param productName 商品名
     * @param quantity 数量
     * @param price 価格
     * @param priority 優先度
     * @return 作成されたタスク
     */
    private static Task createOrderTask(
        String orderId, 
        String productName, 
        int quantity, 
        int price,
        TaskPriority priority
    ) {
        Task task = new Task(orderId, "注文処理: " + productName);
        task.setPriority(priority);
        task.setMaxRetries(3);
        
        // タスクのメタデータ設定
        task.addMetadata("product", productName);
        task.addMetadata("quantity", String.valueOf(quantity));
        task.addMetadata("price", String.valueOf(price));
        task.addMetadata("totalAmount", String.valueOf(quantity * price));
        
        // タスクの実行ロジック
        task.setExecutor(() -> {
            try {
                // ステップ1: 在庫確認（ランダムで遅延）
                System.out.println(String.format(
                    "  [%s] 📦 在庫確認中... (%s × %d)",
                    orderId, productName, quantity
                ));
                Thread.sleep((long)(Math.random() * 500 + 200));
                
                // 在庫不足をシミュレート（数量が多い場合）
                if (quantity > 50 && Math.random() < 0.3) {
                    throw new RuntimeException("在庫不足");
                }
                
                // ステップ2: 決済処理
                System.out.println(String.format(
                    "  [%s] 💳 決済処理中... (¥%,d)",
                    orderId, quantity * price
                ));
                Thread.sleep((long)(Math.random() * 700 + 300));
                
                // 決済エラーをシミュレート（ランダム）
                if (orderId.equals("ORD-005") && Math.random() < 0.5) {
                    throw new RuntimeException("決済エラー");
                }
                
                // ステップ3: 配送手配
                System.out.println(String.format(
                    "  [%s] 🚚 配送手配中...",
                    orderId
                ));
                Thread.sleep((long)(Math.random() * 400 + 200));
                
                // 成功
                return new TaskResult(
                    orderId,
                    true,
                    String.format(
                        "注文完了: %s × %d (合計 ¥%,d)",
                        productName, quantity, quantity * price
                    )
                );
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new TaskResult(orderId, false, "処理が中断されました");
            } catch (Exception e) {
                // エラー時はリトライ対象
                throw new RuntimeException(e.getMessage());
            }
        });
        
        return task;
    }
    
    /**
     * イベントリスナーを設定
     * 
     * @param eventBus イベントバス
     */
    private static void setupEventListeners(EventBus eventBus) {
        // タスク開始イベント
        eventBus.subscribe(TaskEventType.TASK_STARTED, event -> {
            System.out.println(String.format(
                "▶️  [%s] タスク開始: %s (優先度: %s)",
                event.getTaskId(),
                event.getData().get("description"),
                event.getData().get("priority")
            ));
        });
        
        // タスク完了イベント
        eventBus.subscribe(TaskEventType.TASK_COMPLETED, event -> {
            System.out.println(String.format(
                "✅ [%s] タスク完了: %s",
                event.getTaskId(),
                event.getData().get("message")
            ));
        });
        
        // タスク失敗イベント
        eventBus.subscribe(TaskEventType.TASK_FAILED, event -> {
            System.out.println(String.format(
                "❌ [%s] タスク失敗: %s",
                event.getTaskId(),
                event.getData().get("error")
            ));
        });
        
        // リトライイベント
        eventBus.subscribe(TaskEventType.TASK_RETRY, event -> {
            System.out.println(String.format(
                "🔄 [%s] リトライ中... (試行回数: %s/%s) 理由: %s",
                event.getTaskId(),
                event.getData().get("currentRetry"),
                event.getData().get("maxRetries"),
                event.getData().get("error")
            ));
        });
    }
    
    /**
     * 全タスクの完了を待機
     * 
     * @param futures タスクのFutureリスト
     */
    private static void waitForAllTasks(List<Future<TaskResult>> futures) {
        System.out.println("⏳ 全タスクの完了を待機中...\n");
        
        int successCount = 0;
        int failureCount = 0;
        
        for (Future<TaskResult> future : futures) {
            try {
                TaskResult result = future.get();
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("⚠️  処理が中断されました");
                failureCount++;
            } catch (ExecutionException e) {
                System.err.println("⚠️  実行エラー: " + e.getMessage());
                failureCount++;
            }
        }
        
        System.out.println("\n" + "=" .repeat(60));
        System.out.println("\n📈 処理結果サマリー");
        System.out.println(String.format("  ✅ 成功: %d件", successCount));
        System.out.println(String.format("  ❌ 失敗: %d件", failureCount));
        System.out.println(String.format("  📊 成功率: %.1f%%", 
            (successCount * 100.0) / futures.size()));
    }
}
