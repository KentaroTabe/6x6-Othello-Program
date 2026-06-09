package p26x08;

import ap26.Board;
import ap26.Color;
import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;
import static ap26.Color.*;
import java.util.stream.IntStream;

public class relationValue{
    public static int checkFrontier(Board board, int k){
        int i_begin=-1;
        int i_end=1;
        int j_begin=-1;
        int j_end=1;
        int row = k / SIZE;
        int col = k % SIZE;
        if(row==0)i_begin=0;
        else if(row==SIZE-1)i_end=0;
        if(col==0)j_begin=0;
        else if(col==SIZE-1)j_end=0;
        for(int i=i_begin;i<=i_end;i++){
            for(int j=j_begin;j<=j_end;j++){
                if(board.get(k+i*6+j)==NONE) return 1;
            }
        }
        return 0;
    }
    public static int countFrontier(Board board, Color color){
        int count=0;
        for(int k=0;k<Board.LENGTH;k++){
            if(board.get(k)==color) count+=checkFrontier(board,k);
        }
        return count;
    }
}