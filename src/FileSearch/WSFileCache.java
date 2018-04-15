package FileSearch;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WSFileCache {
    VirtualFile m_virtualFile;
    String m_fileName;

    long m_timeStamp;
    long m_modifiedTime;

    List<String> m_Lines;
    List<String> m_LinesLowercase = new ArrayList<String>();

    public void init(VirtualFile file) {
        FSLog.log.info("WSFileCache init: " + file.getName());
        m_virtualFile = file;
        m_fileName = file.getName();
        m_timeStamp = file.getTimeStamp();
        m_modifiedTime = file.getModificationStamp();

        String text = LoadTextUtil.loadText(file).toString();
        m_Lines = Arrays.asList(text.split("\n"));

        for(int i = 0;i < m_Lines.size();++i) {
            m_Lines.set(i,m_Lines.get(i).trim());
            m_LinesLowercase.add(m_Lines.get(i).toLowerCase());
        }
    }
}