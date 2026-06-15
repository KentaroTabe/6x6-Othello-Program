package p26x08;

import ap26.Board;
import static ap26.Board.SIZE;
import ap26.Color;
import static ap26.Color.BLACK;
import static ap26.Color.WHITE;
import ap26.Move;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * α-β 法で次の一手を決めるオセロプレイヤー。
 *
 * <p>unit0 の {@link AlphaBetaPlayer} と探索アルゴリズムの構造はまったく同じ
 * だが、ノード = オセロ盤面 ({@link Board})、ムーブ = オセロの着手 ({@link Move})
 * という具体に置き換わっている。本クラスは「unit0 で学んだ抽象例を
 * 実ゲームに適用する」具体例である。
 *
 * <h2>クラスの責務</h2>
 * <ul>
 *   <li>{@link ap26.Player} を継承し、{@link #think(Board)} で次の一手を返す</li>
 *   <li>内部で {@link MyEval} と α-β 探索を組み合わせる</li>
 *   <li>探索の最大深さ {@link #depthLimit} を持つ</li>
 * </ul>
 *
 * <h2>実装上のトリック</h2>
 *
 * <h3>1. 白番のときの盤面反転</h3>
 * {@link #think(Board)} 内で、自分が白番のときは {@link Board#flipped()} で
 * 盤面の色を反転して「常に黒視点で探索する」ようにしている。これにより
 * {@link #maxSearch} は常に黒の手を選ぶ、{@link #minSearch} は常に白の手を
 * 選ぶ、と固定でき、探索コードに色の分岐を入れずに済む。
 * <p>反転は対称性を利用したトリックで、評価関数 {@link MyEval#value(Board)}
 * が黒視点で書かれていれば、反転後の評価値もまた「反転視点での黒（=
 * 元の白）」の優劣を正しく表す。
 *
 * <h3>2. 最善手の記録 {@code if (depth == 0) this.move = move;}</h3>
 * α-β 探索は普通「評価値」だけを戻り値で返すが、ゲームプレイには
 * 「どの手が最善か」も必要である。本実装では <b>ルートノード (depth == 0)
 * でのみ</b>、α が更新されたタイミングで該当ムーブをフィールド
 * {@link #move} に保存している。
 * <p>戻り値ではなく副作用で持ち回るのは、探索本体のシグネチャを
 * unit0 と同じに保つための簡略化。本格的な実装では「評価値 + 最善手」
 * を構造体で返す方が綺麗。
 *
 * <h3>3. 同点時の手番揺らぎ {@link #order(List)}</h3>
 * α-β は決定的で、同じ評価値の手が複数あると常に同じ手が選ばれる。
 * すると「相手が同じ局面に来たら必ず同じ手」と読まれて単調になる
 * ため、{@link Collections#shuffle} で手順をランダム化している。
 * 本格的な実装では「最善手を優先的に探索する手順並び替え」を入れて
 * カットを最大化するが、本教材では学習しやすさを優先。
 *
 * <h3>4. 全枝同点時のフォールバック {@code this.move = moves.get(0);}</h3>
 * 全子ノードの評価値が初期 α と一致した場合、α 更新が一度も起きず、
 * {@link #move} が {@code null} のままになる。これを防ぐため、ループ前に
 * リストの先頭を仮の最善手として登録している。
 */
public class DynamicPlayer extends ap26.Player {

  private static final long[][] ZOBRIST = new long[Board.LENGTH][2];
    static {
        Random rnd = new Random(2026);
        for (int i = 0; i < Board.LENGTH; i++) {
            ZOBRIST[i][0] = rnd.nextLong(); 
            ZOBRIST[i][1] = rnd.nextLong(); 
        }
    }
  
  private static final int TT_SIZE = 1 << 20; 
  private static final int TT_MASK = TT_SIZE - 1;
  private float[] ttValue = new float[TT_SIZE];

  private long totalConsumedTime = 0;
  private long currentMoveStartTime;
  private long currentMoveTimeLimit;
  private int nodeCount = 0;
  private int firstTurn = 0;
    
  // 学習時や状況に応じて制限時間を変更できるようにインスタンス変数化（大会ルールは60秒=60000ms。バッファ込で58秒）
  private long maxGameTimeMs = 58000;
  private static class TimeoutException extends Exception {}
  
  /** デフォルトのプレイヤー名（リーグ戦で識別用）。*/
  static final String MY_NAME = "DI24";

  /** 評価関数。{@link MyEval} を参照。*/
  DynamicEval eval;

  /** 探索の最大深さ。深いほど強いが計算時間が指数的に増える。*/
  int depthLimit;

  /** ルートで決定した最善手（戻り値ではなく副作用で持ち回る）。*/
  Move move;

  /** 探索用の内部盤面。相手の手番を逐次反映する。*/
  Board board;

  /** デフォルトコンストラクタ。深さ 2 で構築。*/
  public DynamicPlayer(Color color) {
    this(MY_NAME, color, new DynamicEval(), 8);
  }

  /** 全パラメータを明示するコンストラクタ。*/
  public DynamicPlayer(String name, Color color, DynamicEval eval, int depthLimit) {
    super(name, color);
    this.eval = eval;
    this.depthLimit = depthLimit;
    this.ttValue = new float[TT_SIZE];
  }

  /** 名前と深さを指定するコンストラクタ（評価関数はデフォルト）。*/
  public DynamicPlayer(String name, Color color, int depthLimit) {
    this(name, color, new DynamicEval(), depthLimit);
  }

  /**
   * ゲーム開始時に呼ばれる。リーグ戦システムから渡される {@link Board} を
   * 内部の {@link MyBoard} に複製する。
   */
  @Override
  public void setBoard(Board board) {
    this.board = board.clone();
    this.eval.InitializeEval(board);
    DynamicEval.blockArrange(board, PRIORITY);
  }

  /** 自分が黒番か。*/
  boolean isBlack() {
    return getColor() == BLACK;
  }

  /**
   * 次の一手を返す。ゲームシステムから手番が来るたびに呼ばれる。
   *
   * <p>処理手順:
   * <ol>
   *   <li>相手の直前手 ({@code board.getMove()}) を内部盤面に反映</li>
   *   <li>合法手が無ければパス</li>
   *   <li>あれば α-β 探索で最善手 {@link #move} を決定</li>
   *   <li>決定した手を内部盤面にも反映して返す</li>
   * </ol>
   */
  @Override
  public Move think(Board board) {
    // 1. 相手の直前手を反映
    this.board = this.board.placed(board.getMove());
    currentMoveStartTime = System.currentTimeMillis();
    nodeCount = 0;
    int emptyCount = 0;
        for (int k = 0; k < Board.LENGTH; k++) {
            if (board.get(k) == Color.NONE) emptyCount++;
        }

    long timeLeft = maxGameTimeMs - totalConsumedTime;
    if (timeLeft < 500) timeLeft = 500;

    if (this.board.findLegalMoves(getColor()).get(0).isPass()) {
      // 2. 合法手なし → パス
      this.move = Move.ofPass(getColor());
    } else {
      int myRemainingTurns = Math.max(1, emptyCount / 2);
            
            // 均等割りではなく、1.5倍の係数をかけて深読みを優先する（時間を前借りするイメージ）
            currentMoveTimeLimit = (long) ((timeLeft / (double) myRemainingTurns) * 2.0);
            
            /**
            // ただし、1手で残り時間の40%以上を使わないようセーフティをかける
            long maxAllowed = (long) (timeLeft * 0.6); 
            if (currentMoveTimeLimit > maxAllowed) {
                currentMoveTimeLimit = maxAllowed;
            }
                */
            
            // 制限時間が長ければ最低保証時間を設定
            if (maxGameTimeMs >= 50000 && currentMoveTimeLimit < 1500) {
                currentMoveTimeLimit = Math.min(1500, timeLeft - 500);
            }
      // 3. 黒視点で探索するため、白番のときは盤面を反転

      Board searchBoard = isBlack() ? this.board.clone() : this.board.flipped();
      this.move = order(searchBoard.findLegalMoves(BLACK)).get(0);

      // 副作用で this.move に最善手が記録される
      int depthMax = this.depthLimit;
      if(firstTurn<1){
        firstTurn++;
        this.depthLimit = 0;
        if(getColor() == WHITE){
          if(board.get(8)==BLACK) this.move = Move.of(19, WHITE);
          else if(board.get(13)==BLACK) this.move = Move.of(10, WHITE);
          else if(board.get(22)==BLACK) this.move = Move.of(26, WHITE);
          else this.move = Move.of(16, WHITE);
          
        }
      }
      while(true)
      {
        try{
        maxSearch(searchBoard, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0);
        break;
        }catch(TimeoutException e){}
        this.depthLimit -= 1;
      }
      this.depthLimit = depthMax;

      // 反転して探索したので、最善手の色を自分の色に戻す
      this.move = this.move.colored(getColor());
    }

    // 4. 自分の指した手も内部盤面に反映
    this.board = this.board.placed(this.move);
    long endTime = System.currentTimeMillis();
    totalConsumedTime += (endTime - currentMoveStartTime);
    return this.move;
  }

  /**
   * α-β 探索の max 側。unit0 の {@link AlphaBetaPlayer#maxSearch} と
   * ロジックは同じ。違いは「ルート (depth == 0) で最善手を {@link #move}
   * に保存する」点だけ。
   */
  float maxSearch(Board currentBoard, float alpha, float beta, int depth)throws TimeoutException {
    //checkTime();
    if (isTerminal(currentBoard, depth)) {
      long hash = getHash(currentBoard, true);
      int index = (int) (hash & TT_MASK);
      float val = ttValue[index];
      if(val==0){
        val = this.eval.value(currentBoard);
        ttValue[index] = val;
      }
      return val;
    }

    // 探索は常に黒視点なので、ここでは黒の合法手を生成
    List<Move> moves = currentBoard.findLegalMoves(BLACK);
    moves = order(moves);

    // フォールバック: 全枝が初期 α と同点だった場合に備えて
    // 暫定の最善手を仮登録（後でループ内の更新が一度も起きないと困るため）
    if (depth == 0) {
      this.move = moves.get(0);
    }

    // ループ変数は this.move フィールドと混同しないよう nextMove と命名
    for (Move nextMove : moves) {
      Board nextBoard = currentBoard.placed(nextMove);
      float childValue = minSearch(nextBoard, alpha, beta, depth + 1);

      if (childValue > alpha) {
        alpha = childValue;
        if (depth == 0) {
          // ルートでの最善手を記録（戻り値ではなく副作用で）
          this.move = nextMove;
        }
      }

      if (alpha >= beta) {
        // β カット: 祖先の min はこれ以上の値を許さない
        break;
      }
    }

    return alpha;
  }

  /**
   * α-β 探索の min 側。unit0 の {@link AlphaBetaPlayer#minSearch} と同じ。
   * 探索は黒視点で進めるので、min 側は白（= 相手）の手を生成する。
   */
  float minSearch(Board currentBoard, float alpha, float beta, int depth)throws TimeoutException {
    //checkTime();
    if (isTerminal(currentBoard, depth)) {
      long hash = getHash(currentBoard, false);
      int index = (int) (hash & TT_MASK);
      float val = ttValue[index];
      if(val==0){
        val = this.eval.value(currentBoard);
        ttValue[index] = val;
      }
      return val;
    }

    List<Move> moves = currentBoard.findLegalMoves(WHITE);
    moves = order(moves);

    for (Move nextMove : moves) {
      Board nextBoard = currentBoard.placed(nextMove);
      float childValue = maxSearch(nextBoard, alpha, beta, depth + 1);
      beta = Math.min(beta, childValue);

      if (alpha >= beta) {
        // α カット
        break;
      }
    }

    return beta;
  }

  /** 探索打ち切り判定。unit0 と同じ。*/
  boolean isTerminal(Board currentBoard, int depth) {
    return currentBoard.isEnd() || depth > this.depthLimit;
  }

  /**
   * 探索する手順を並び替える。
   * <p>本実装ではランダムシャッフルだけ。同じ評価値の手が複数あったとき、
   * 毎回同じ手を選んで単調になるのを避ける。
   * <p>本格的な実装では「過去の探索で良かった手を先に試す」など、
   * α-β カットを最大化する並び替えを入れる。
   */
  List<Move> order(List<Move> moves) {
    List<Move> sorted = new ArrayList<>(moves);

    // マスの優先度（静的評価値）に基づいて降順（大きい順）にソートする
    // m2 の優先度から m1 の優先度を比較することで降順になる
    sorted.sort((m1, m2) -> Integer.compare(getMovePriority(m2), getMovePriority(m1)));

    return sorted;
  }
  
  /**
   * 着手マスの優先度を返すヘルパーメソッド。
   * 6x6盤面における簡易的な静的評価値。角を最大、角の斜め内側(Xマス)を最小とする。
   */
  float[][] PRIORITY = {
       {100, -20,  10,  10, -20, 100},
       {-20, -50,  -5,  -5, -50, -20},
        {10,  -5,   0,   0,  -5,  10},
        {10,  -5,   0,   0,  -5,  10},
       {-20, -50,  -5,  -5, -50, -20},
       {100, -20,  10,  10, -20, 100}
    };
  int getMovePriority(Move move) {
    if (move.isPass()) {
        return 0;
    }

    int k = move.getIndex();
    int row = k / SIZE;
    int col = k % SIZE;
    
    // 6x6 用の優先度テーブル (1次元配列でアクセスを高速化)
    // 評価関数(DynamicEval)の重みと似ているが、探索順序を決めるだけの
    // 大雑把な値で十分機能する
    return (int)(PRIORITY[row][col]);
  }

  private long getHash(Board board, boolean isBlackTurn) {
        long hash = 0;
        for (int i = 0; i < Board.LENGTH; i++) {
            Color c = board.get(i);
            if (c == Color.BLACK) hash ^= ZOBRIST[i][0];
            else if (c == Color.WHITE) hash ^= ZOBRIST[i][1];
        }
        if (!isBlackTurn) hash ^= 0x123456789ABCDEFL;
        return hash;
    }

    private void checkTime() throws TimeoutException {
        nodeCount++;
        if ((nodeCount & 4095) == 0) { 
            if (System.currentTimeMillis() - currentMoveStartTime > currentMoveTimeLimit) {
                throw new TimeoutException();
            }
        }
    }
}
