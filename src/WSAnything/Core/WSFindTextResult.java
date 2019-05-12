package WSAnything.Core;

import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

import com.intellij.openapi.util.Pair;

public class WSFindTextResult {
    public VirtualFile m_virtualFile;
    public boolean m_bConsiderFileName = false;
    public String m_strFileName;
    public String m_strLineLowercase;
    public String m_strLine;
    public int m_nLineOffset;
    public int m_nLineIndex;
    public int nBeginIndex;
    public int nEndIdex;
    public String m_textBoxText;
    public List<Pair<Integer, Integer>> m_ListMatchTextIndexs = Lists.newArrayList();
    public List<Pair<Integer, Integer>> m_ListMatchFileNameIndex = Lists.newArrayList();


    public String tostring() {
        return m_virtualFile.getName() + "(" + m_nLineIndex + 1 + "):" + "[" + nBeginIndex + "," + nEndIdex + "]" + m_strLine;

    }
}