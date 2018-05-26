package FileSearch.Core;

import FileSearch.FSLog;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WSFindTextResult {
    public VirtualFile m_virtualFile;
    public String m_strLineLowercase;
    public int m_nLineNum;
    public int nBeginIndex;
    public int nEndIdex;

    public String tostring() {
        return m_virtualFile.getName() + "(" + m_nLineNum + "):" + "[" + nBeginIndex + "," + nEndIdex + "]" + m_strLineLowercase;

    }
}