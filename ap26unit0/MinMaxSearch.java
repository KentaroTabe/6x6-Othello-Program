/**
 * min-max 法によるゲーム木探索プレイヤー。
 *
 * <h2>min-max 法とは</h2>
 * 二人零和ゲーム（一方の得 = 他方の損）でよく使われる探索手法。
 * <ul>
 *   <li>自分（max プレイヤー）は評価値が <b>最大</b> になる手を選ぶ。</li>
 *   <li>相手（min プレイヤー）は評価値が <b>最小</b> になる手を選ぶ（自分から見て）。</li>
 *   <li>葉まで読みきれない場合は {@code depthLimit} で打ち切り、評価関数で
 *       「その時点の局面の優劣」を推定する。</li>
 * </ul>
 *
 * <h2>{@link #maxSearch} と {@link #minSearch} の対称性</h2>
 * 2 つのメソッドはほぼ同型で、次の 3 点だけが異なる。
 * <ol>
 *   <li>初期値: {@code -∞} か {@code +∞} か</li>
 *   <li>更新: {@code Math.max} か {@code Math.min} か</li>
 *   <li>再帰呼び出し先: {@code minSearch} か {@code maxSearch} か（互いに呼び合う）</li>
 * </ol>
 * この対称性は negaMax と呼ばれる形にまとめると 1 つの関数で書けるが、
 * 本教材では「max と min の役割の違い」を理解しやすくするために分けて書く。
 *
 * <h2>探索木の例（Node.java のゲーム木）での動作</h2>
 * <pre>
 *               A           ← max: 子の値 (2.0, 1.0) の最大 → 2.0
 *              / \
 *             B   C         ← min
 *            / \ / \
 *           D E F G         ← max（葉なので即評価）
 *          3 2 1 4
 *
 *   minSearch(B) = min(maxSearch(D)=3, maxSearch(E)=2) = 2
 *   minSearch(C) = min(maxSearch(F)=1, maxSearch(G)=4) = 1
 *   maxSearch(A) = max(2, 1) = 2.0
 * </pre>
 */
class MinMaxPlayer {

  /**
   * 探索の最大深さ。{@code depth > depthLimit} になった時点で打ち切り、
   * 葉でなくても評価関数で値を返す。教材例では木の深さが 3 なので、
   * {@code depthLimit = 4} なら必ず葉まで読みきれる。
   */
  int depthLimit = 4;

  /**
   * 訪問したノード数（葉と内部ノードの合計）。
   * α-β との比較に用いる。{@link #maxSearch} と {@link #minSearch} が
   * 呼ばれるたびに 1 増える。
   */
  int visited = 0;

  /**
   * max プレイヤー（自分）の手番での探索。
   * 子ノードの中で {@link #minSearch} の戻り値が <b>最大</b> となる枝を選ぶ。
   *
   * @param node  現在のノード（自分が指す番）
   * @param depth 現在の深さ（根を 0 とする）
   * @return このノードの評価値（max プレイヤー視点）
   */
  float maxSearch(Node node, int depth) {
    this.visited++;
    if (isTerminal(node, depth)) {
      return Eval.shared.value(node);
    }

    // 初期値は負の無限大。最初の子の値で必ず更新される。
    float v = Float.NEGATIVE_INFINITY;

    for (Move move : node.getMoves()) {
      Node nextNode = node.perform(move);
      // 次は相手 (min) の手番なので minSearch を呼ぶ
      float childValue = minSearch(nextNode, depth + 1);
      v = Math.max(v, childValue);
    }

    return v;
  }

  /**
   * min プレイヤー（相手）の手番での探索。
   * 子ノードの中で {@link #maxSearch} の戻り値が <b>最小</b> となる枝を選ぶ
   *（相手は自分にとって不利な手を選ぶと仮定する）。
   *
   * @param node  現在のノード（相手が指す番）
   * @param depth 現在の深さ
   * @return このノードの評価値（max プレイヤー視点）
   */
  float minSearch(Node node, int depth) {
    this.visited++;
    if (isTerminal(node, depth)) {
      return Eval.shared.value(node);
    }

    // 初期値は正の無限大。最初の子の値で必ず更新される。
    float v = Float.POSITIVE_INFINITY;

    for (Move move : node.getMoves()) {
      Node nextNode = node.perform(move);
      // 次は自分 (max) の手番なので maxSearch を呼ぶ
      float childValue = maxSearch(nextNode, depth + 1);
      v = Math.min(v, childValue);
    }

    return v;
  }

  /**
   * 探索を打ち切るべきか。
   * <ul>
   *   <li>葉に到達した → 評価値が確定するので打ち切り</li>
   *   <li>深さ制限を超えた → これ以上深く読まず、評価関数で推定</li>
   * </ul>
   */
  boolean isTerminal(Node node, int depth) {
    return node.isGoal() || depth > this.depthLimit;
  }
}
