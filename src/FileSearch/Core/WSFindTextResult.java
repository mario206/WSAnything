package FileSearch.Core;

import com.intellij.openapi.vfs.VirtualFile;

public class WSFindTextResult {
    public VirtualFile m_virtualFile;
    public String m_strLineLowercase;
    public String m_strLine;
    public int m_nLineOffset;
    public int m_nLineIndex;
    public int nBeginIndex;
    public int nEndIdex;

    public String tostring() {
        return m_virtualFile.getName() + "(" + m_nLineIndex + 1 + "):" + "[" + nBeginIndex + "," + nEndIdex + "]" + m_strLine;

    }
}