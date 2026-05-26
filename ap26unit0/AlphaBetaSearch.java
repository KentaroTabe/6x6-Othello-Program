/**
 * α-β 法（アルファ・ベータ枝刈り）によるゲーム木探索プレイヤー。
 *
 * <h2>α-β 法とは</h2>
 * min-max 法と同じ結果（最善手の評価値）を返すが、「これ以上探索しても
 * 結果が変わらない枝」を途中で打ち切る（=枝刈り）ことで、無駄な計算を
 * 大幅に減らせる手法。
 *
 * <h2>α と β の意味</h2>
 * 探索中、現在のノードで「呼び出し元（祖先ノード）が <b>既に保証している</b>
 * 評価値の範囲」を 2 つの数で持ち回る。
 * <ul>
 *   <li>{@code α} (alpha) = max 側がこれまでに保証できた <b>最大値</b>。
 *       「私は少なくとも α は取れる」というレベル。</li>
 *   <li>{@code β} (beta)  = min 側がこれまでに保証できた <b>最小値</b>。
 *       「相手は α を超える結果は許さない、α 以上は β で抑える」というレベル。</li>
 * </ul>
 * 探索の興味は区間 {@code (α, β)} の内側にあり、ここに入らない値は
 * 「祖先のどれかが既に却下している」ため最終結果に影響しない。
 *
 * <h2>枝刈り条件 {@code α >= β} の意味</h2>
 * α が β 以上になった時点で、
 * <ul>
 *   <li>max 側の検討中: 「私はもう少なくとも α が取れる」と分かったが、
 *       β は祖先の min がこれ以上は許さない上限。α ≥ β ということは、
 *       相手は私にこの分岐を選ばせる前に別の手を選ぶ。
 *       <b>→ この子は祖先で却下されるので、残りの兄弟を見ても無駄。</b></li>
 *   <li>min 側でも対称: β ≤ α なら、私（min）はもうそれ以下に抑えたが、
 *       祖先の max は α 未満の手を選ばないので、残りの兄弟を見ても無駄。</li>
 * </ul>
 * これを <b>β カット</b>（max 側）/ <b>α カット</b>（min 側）と呼ぶ。
 *
 * <h2>min-max からの差分</h2>
 * <ol>
 *   <li>引数に {@code alpha}, {@code beta} を追加（範囲のしぼり込み）</li>
 *   <li>ループ内で {@code alpha} (max 側) / {@code beta} (min 側) を更新</li>
 *   <li>{@code if (alpha >= beta) break;} のカット</li>
 *   <li>戻り値は更新された {@code alpha} / {@code beta} そのもの</li>
 * </ol>
 *
 * <h2>探索木の例 (Node.java) でのカット動作</h2>
 * <pre>
 *               A           ← max (α=-∞, β=+∞)
 *              / \
 *             B   C         ← min
 *            / \ / \
 *           D E F G         ← 葉
 *          3 2 1 4
 *
 *   maxSearch(A): α=-∞, β=+∞
 *     minSearch(B): α=-∞, β=+∞
 *       maxSearch(D)=3 → β=min(+∞,3)=3
 *       maxSearch(E)=2 → β=min(3,2)=2 → return 2
 *     α=max(-∞, 2)=2
 *     minSearch(C): α=2, β=+∞
 *       maxSearch(F)=1 → β=min(+∞,1)=1
 *       ★ α=2 ≥ β=1 → 枝刈り! G は評価されない
 *       return 1
 *     α=max(2, 1)=2
 *   return 2.0
 * </pre>
 * min-max では葉を 4 つすべて評価したが、α-β では G が省略され 3 つだけ。
 * 大きな木ほどこの効果は劇的で、最適な手順並び替えと組み合わせれば
 * 計算量は O(b^d) → O(b^(d/2)) まで減らせる。
 */
class AlphaBetaPlayer {

  /** 探索の最大深さ。{@link MinMaxPlayer#depthLimit} と同じ役割。*/
  int depthLimit = 4;

  /**
   * 訪問したノード数。{@link MinMaxPlayer#visited} と比較すると、
   * α-β 枝刈りでどれだけノードが省略されたかが分かる。
   */
  int visited = 0;

  /**
   * max プレイヤーの手番での探索。
   *
   * @param node  現在のノード
   * @param alpha これまでに max 側が保証できた最大値
   * @param beta  これ以上は min 側が許さない上限
   * @param depth 現在の深さ
   * @return このノードの評価値（max プレイヤー視点）
   */
  float maxSearch(Node node, float alpha, float beta, int depth) {
    this.visited++;
    if (isTerminal(node, depth)) {
      return Eval.shared.value(node);
    }

    for (Move move : node.getMoves()) {
      Node nextNode = node.perform(move);
      // 次は相手 (min) の手番。引数の α/β はそのまま渡す。
      float childValue = minSearch(nextNode, alpha, beta, depth + 1);

      // 「私は少なくとも childValue が取れる」を反映
      alpha = Math.max(alpha, childValue);

      // α ≥ β: 祖先の min はこれ以上の値を許さない。
      // → 残りの兄弟ノードは見ても無駄なので打ち切る (β カット)。
      if (alpha >= beta) {
        break;
      }
    }

    return alpha;
  }

  /**
   * min プレイヤーの手番での探索。{@link #maxSearch} と完全に対称。
   *
   * @param node  現在のノード
   * @param alpha これ以上は max 側が許さない下限
   * @param beta  これまでに min 側が保証できた最小値
   * @param depth 現在の深さ
   * @return このノードの評価値（max プレイヤー視点）
   */
  float minSearch(Node node, float alpha, float beta, int depth) {
    this.visited++;
    if (isTerminal(node, depth)) {
      return Eval.shared.value(node);
    }

    for (Move move : node.getMoves()) {
      Node nextNode = node.perform(move);
      // 次は自分 (max) の手番
      float childValue = maxSearch(nextNode, alpha, beta, depth + 1);

      // 「相手はもう childValue 以下に抑えた」を反映
      beta = Math.min(beta, childValue);

      // α ≥ β: 祖先の max はこれ未満の値を選ばない。
      // → 残りの兄弟ノードは見ても無駄なので打ち切る (α カット)。
      if (alpha >= beta) {
        break;
      }
    }

    return beta;
  }

  /** {@link MinMaxPlayer#isTerminal(Node, int)} と同じ。*/
  boolean isTerminal(Node node, int depth) {
    return node.isGoal() || depth > this.depthLimit;
  }
}
