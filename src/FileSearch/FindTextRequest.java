package FileSearch;

import java.util.function.Function;

public class FindTextRequest {
    static class Pattern {
        String[] vec;
    }

    public Pattern m_pattern;
    int m_nMaxResult;
    boolean m_bConsiderFileName;
    public String m_TextBoxText;
    Function<Integer,Integer> m_finishCallBack;
}
