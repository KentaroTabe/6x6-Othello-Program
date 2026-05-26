/**
 * unit0 の動作確認エントリポイント。
 *
 * <p>{@link MinMaxPlayer} と {@link AlphaBetaPlayer} の両方で
 * {@link Node} で定義したゲーム木を探索し、結果と訪問ノード数を表示する。
 *
 * <h2>期待出力</h2>
 * <pre>
 *   min-max:    value=2.0  visited=??
 *   alpha-beta: value=2.0  visited=??
 * </pre>
 * 値はどちらも 2.0（α-β は枝刈りをしても結果は変わらないため）。
 * 訪問ノード数は α-β の方が少ない（カットで省略される枝があるため）。
 *
 * <h2>確認できること</h2>
 * <ul>
 *   <li>min-max 法と α-β 法は同じ最善値 2.0 を返す</li>
 *   <li>木のルート A から見て、最善は B 経由（A→B→E で値 2.0）</li>
 *   <li>訪問ノード数の差が α-β の効果（枝刈りの実効性）を示す</li>
 * </ul>
 */
public class Main {

  public static void main(String[] args) {
    runMinMax();
    runAlphaBeta();
  }

  /** min-max 法でルート A の評価値を求めて表示する。*/
  static void runMinMax() {
    MinMaxPlayer player = new MinMaxPlayer();
    Node root = new Node("A");
    float value = player.maxSearch(root, 0);
    System.out.printf("min-max:    value=%.1f  visited=%d%n",
        value, player.visited);
  }

  /** α-β 法でルート A の評価値を求めて表示する。*/
  static void runAlphaBeta() {
    AlphaBetaPlayer player = new AlphaBetaPlayer();
    Node root = new Node("A");
    // 初期 α/β はそれぞれ -∞, +∞ にする。
    // 「まだ何の制約も無い」状態を表すための定石。
    float alpha = Float.NEGATIVE_INFINITY;
    float beta = Float.POSITIVE_INFINITY;
    float value = player.maxSearch(root, alpha, beta, 0);
    System.out.printf("alpha-beta: value=%.1f  visited=%d%n",
        value, player.visited);
  }
}
