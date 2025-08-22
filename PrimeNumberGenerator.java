/**
 * PrimeNumberGenerator.java
 *
 * 概要:
 * - エラトステネスの篩を用いて指定範囲内の素数を効率的に列挙するプログラム
 * - コマンドライン引数で「上限値」を指定できる
 * - 実行例: java PrimeNumberGenerator 100
 *
 * 処理の流れ:
 * 1. 引数で上限値を受け取る
 * 2. エラトステネスの篩を使って素数を判定する
 * 3. 素数を標準出力に表示する
 */

import java.util.ArrayList;
import java.util.List;

public class PrimeNumberGenerator {

    /**
     * エラトステネスの篩を用いて素数を生成するメソッド
     *
     * @param limit 素数を探す上限値
     * @return 素数のリスト
     */
    public static List<Integer> generatePrimes(int limit) {
        boolean[] isComposite = new boolean[limit + 1]; // 素数かどうかの判定配列
        List<Integer> primes = new ArrayList<>();

        for (int i = 2; i * i <= limit; i++) {
            if (!isComposite[i]) {
                for (int j = i * i; j <= limit; j += i) {
                    isComposite[j] = true; // 合成数にマーク
                }
            }
        }

        for (int i = 2; i <= limit; i++) {
            if (!isComposite[i]) {
                primes.add(i);
            }
        }

        return primes;
    }

    public static void main(String[] args) {
        // コマンドライン引数チェック
        if (args.length != 1) {
            System.out.println("使用方法: java PrimeNumberGenerator <上限値>");
            return;
        }

        try {
            int limit = Integer.parseInt(args[0]);
            List<Integer> primes = generatePrimes(limit);

            System.out.println("素数一覧 (～ " + limit + "):");
            System.out.println(primes);

        } catch (NumberFormatException e) {
            System.out.println("エラー: 上限値は整数で入力してください");
        }
    }
}
