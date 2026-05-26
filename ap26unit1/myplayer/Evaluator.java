package myplayer;

import ap26.Board;
import ap26.Color;
import ap26.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class Evaluator {

  public static void main(String[] args) {
    int maxDepth = 7; // 最大深さ
    int gamesPerRole = 25; // 先手・後手それぞれ25局（計50局）
    int totalGames = gamesPerRole * 2;

    PrintStream originalOut = System.out;

    try {
      File outDir = new File("outputs");
      if (!outDir.exists()) {
        outDir.mkdirs();
      }

      PrintStream logOut = new PrintStream(new FileOutputStream("outputs/game_logs.txt"));
      PrintStream resultOut = new PrintStream(new FileOutputStream("outputs/evaluation_results.txt"));

      System.setOut(logOut);

      originalOut.println("深さ差1 (D0vsD1 〜 D6vsD7) の評価を開始します。");
      originalOut.println("※深さ6や7の対局は非常に時間がかかる場合があります。");
      originalOut.println("盤面ログ: outputs/game_logs.txt");
      originalOut.println("集計結果: outputs/evaluation_results.txt\n");

      // 深さd と d+1 の対戦を d=0 から d=6 まで回す (最大深さ7)
      for (int d = 0; d < maxDepth; d++) {
        int d1 = d;
        int d2 = d + 1;

        // D1 (浅い方) の成績
        int d1WinsAsBlack = 0;
        int d1WinsAsWhite = 0;
        float d1TotalTime = 0f;

        // D2 (深い方) の成績
        int d2WinsAsBlack = 0;
        int d2WinsAsWhite = 0;
        float d2TotalTime = 0f;

        int draws = 0;

        originalOut.printf("=== Depth %d vs Depth %d の対局を行っています... ===\n", d1, d2);

        // --- 前半戦: D1(先手/黒) vs D2(後手/白) ---
        for (int i = 0; i < gamesPerRole; i++) {
          Player black = new MyPlayer("D" + d1, Color.BLACK, d1);
          Player white = new MyPlayer("D" + d2, Color.WHITE, d2);
          GameResult res = playOneGame(black, white);

          if (res.score > 0)
            d1WinsAsBlack++;
          else if (res.score < 0)
            d2WinsAsWhite++;
          else
            draws++;

          d1TotalTime += res.blackTime;
          d2TotalTime += res.whiteTime;
        }

        // --- 後半戦: D2(先手/黒) vs D1(後手/白) ---
        for (int i = 0; i < gamesPerRole; i++) {
          Player black = new MyPlayer("D" + d2, Color.BLACK, d2);
          Player white = new MyPlayer("D" + d1, Color.WHITE, d1);
          GameResult res = playOneGame(black, white);

          if (res.score > 0)
            d2WinsAsBlack++;
          else if (res.score < 0)
            d1WinsAsWhite++;
          else
            draws++;

          d2TotalTime += res.blackTime;
          d1TotalTime += res.whiteTime;
        }

        // --- 集計 ---
        int d1TotalWins = d1WinsAsBlack + d1WinsAsWhite;
        int d2TotalWins = d2WinsAsBlack + d2WinsAsWhite;

        double d1OverallWinRate = (double) d1TotalWins / totalGames * 100;
        double d1BlackWinRate = (double) d1WinsAsBlack / gamesPerRole * 100;
        double d1WhiteWinRate = (double) d1WinsAsWhite / gamesPerRole * 100;
        float d1AvgTime = d1TotalTime / totalGames;

        double d2OverallWinRate = (double) d2TotalWins / totalGames * 100;
        double d2BlackWinRate = (double) d2WinsAsBlack / gamesPerRole * 100;
        double d2WhiteWinRate = (double) d2WinsAsWhite / gamesPerRole * 100;
        float d2AvgTime = d2TotalTime / totalGames;

        // --- 出力文字列のフォーマット ---
        String resultStr = String.format(
            "Depth %d vs Depth %d\n" +
                "  - D%d成績: %2d勝 [先手: %5.1f%%, 後手: %5.1f%%] (全体勝率: %5.1f%%) | 平均考慮時間: %6.3f 秒/局\n" +
                "  - D%d成績: %2d勝 [先手: %5.1f%%, 後手: %5.1f%%] (全体勝率: %5.1f%%) | 平均考慮時間: %6.3f 秒/局\n" +
                "  - 引分   : %2d\n",
            d1, d2,
            d1, d1TotalWins, d1BlackWinRate, d1WhiteWinRate, d1OverallWinRate, d1AvgTime,
            d2, d2TotalWins, d2BlackWinRate, d2WhiteWinRate, d2OverallWinRate, d2AvgTime,
            draws);

        // 結果をファイルとコンソールへ書き出す
        resultOut.println(resultStr);
        originalOut.println(resultStr);
      }

      logOut.close();
      resultOut.close();
      System.setOut(originalOut);
      originalOut.println("\nすべての評価が完了しました！");

    } catch (Exception e) {
      originalOut.println("エラーが発生しました: " + e.getMessage());
    }
  }

  static class GameResult {
    int score;
    float blackTime;
    float whiteTime;
  }

  private static GameResult playOneGame(Player black, Player white) {
    Board board = new MyBoard();
    MyGame game = new MyGame(board, black, white);

    game.play();

    GameResult res = new GameResult();
    res.score = game.board.score();
    res.blackTime = game.times.getOrDefault(Color.BLACK, 0f);
    res.whiteTime = game.times.getOrDefault(Color.WHITE, 0f);

    return res;
  }
}