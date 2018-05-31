package FileSearch.Core;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.function.Function;

public class FindTextRequest {
    static class Pattern {
        String[] vec = new String[0];
    }
    public Pattern m_pattern = new Pattern();
    public int m_nMaxResult;
    public boolean m_upToResultCntLimit = false;
    public int m_nResultFileCnt = 0;
    public boolean m_bConsiderFileName;
    public String m_TextBoxText;
    public Function<Object,Integer> m_finishCallBack;
    public int m_currIndex = 0;
    public List<VirtualFile> m_searchFiles;

    public void setString(String str) {
        m_TextBoxText = str;
        m_pattern.vec = m_TextBoxText.split("\\s+");
    }
}
