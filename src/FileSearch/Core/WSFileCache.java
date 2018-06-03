package FileSearch.Core;

import FileSearch.FSLog;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WSFileCache {
    VirtualFile m_virtualFile;
    String m_fileName;

    long m_timeStamp;
    long m_DocumentModifiedTime = 0;

    List<String> m_Lines;
    List<String> m_LinesLowercase = new ArrayList<String>();

    boolean m_bIsSizeValid = true;

    public void init(VirtualFile file) {
        //FSLog.log.info("WSFileCache init: " + file.getName());
        String text = LoadTextUtil.loadText(file).toString();
        updateByText(file,text);
    }

    public void updateFromUnSaveDocument(VirtualFile file, Document document) {
        long time = document.getModificationStamp();
        long lastTime = this.getDocumentModifiedTime();
        if(lastTime != time) {
            String text = document.getText();
            FSLog.log.info("updateFromUnSaveDocument " + file.getName());
            this.updateByText(file,text);
            this.setDocumentModifiedTime(time);
        }
    }
    public void updateByText(VirtualFile file,String text) {
        int txtLength = text.length();
        if(txtLength > WSConfig.MaxFileSize) {
            m_bIsSizeValid = false;
            FSLog.log.warn(file.getName() + " size = " + txtLength + " is bigger Than " + WSConfig.MaxFileSize + "byte,will no be cached!");
            return;
        }
        m_Lines = Arrays.asList(text.split("\n"));
        m_LinesLowercase.clear();
        for(int i = 0;i < m_Lines.size();++i) {
            //m_Lines.set(i,m_Lines.get(i).trim());
            m_Lines.set(i,m_Lines.get(i));
            m_LinesLowercase.add(m_Lines.get(i).toLowerCase());
        }
        m_virtualFile = file;
        m_fileName = file.getName();
        m_timeStamp = file.getTimeStamp();
    }

    public boolean getIsSizeValid() {
        return m_bIsSizeValid;
    }
    public void setDocumentModifiedTime(long time) {
        m_DocumentModifiedTime = time;
    }
    public long getDocumentModifiedTime() {
        return m_DocumentModifiedTime;
    }
}