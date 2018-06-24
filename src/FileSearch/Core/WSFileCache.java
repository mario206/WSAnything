package FileSearch.Core;

import FileSearch.FSLog;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.io.*;


public class WSFileCache implements java.io.Serializable {
    transient VirtualFile m_virtualFile;
    /// Serializable
    String m_fileName;
    String m_fileNameLowerCase;
    long m_timeStamp;
    long m_DocumentModifiedTime = 0;
    List<String> m_Lines = new ArrayList<>();
    List<String> m_LinesLowercase = new ArrayList<>();
    List<Integer> m_LineOffSets = new ArrayList<Integer>();
    boolean m_bReadSuccess = true;
    boolean m_bIsTempFile = false;
    /// Serializable

/*    public void writeObject(ObjectOutputStream outputStream) throws IOException{
        outputStream.writeObject(m_fileName);
        outputStream.writeObject(m_timeStamp);
        outputStream.writeObject(m_DocumentModifiedTime);
        outputStream.writeObject(m_Lines);
        outputStream.writeObject(m_LinesLowercase);
        outputStream.writeObject(m_LineOffSets);
        outputStream.writeObject(m_bReadSuccess);
    }
    public void readObject(ObjectInputStream inputStream) throws IOException,ClassNotFoundException{
        inputStream.defaultReadObject();
        m_fileName = (String) inputStream.readObject();
        m_timeStamp = (long) inputStream.readObject();
        m_DocumentModifiedTime = (long) inputStream.readObject();
        m_Lines = (ArrayList<String>) inputStream.readObject();
        m_LinesLowercase = (ArrayList<String>) inputStream.readObject();
        m_LineOffSets = (ArrayList<Integer>) inputStream.readObject();
        m_bReadSuccess = (boolean) inputStream.readObject();
    }*/

    public void init(VirtualFile file) {
        //FSLog.log.info("WSFileCache init: " + file.getName());
        try {
            String text = LoadTextUtil.loadText(file).toString();
            updateByText(file,text);
        } catch (Exception e) {
            m_bReadSuccess = false;
        }
    }
    public void setIsTmpFile() {
        this.m_bIsTempFile = true;
    }
    public boolean getIsTmpFile() {
        return this.m_bIsTempFile;
    }
    public void updateFromVirtualFile(VirtualFile file) {
        m_virtualFile = file;
        m_DocumentModifiedTime = 0;

        if(m_timeStamp != file.getTimeStamp()) {
            this.init(file);
        }
    }

    public boolean isReadSuccess() {
        return m_bReadSuccess;
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
        m_Lines = Arrays.asList(text.split("\n"));
        m_LinesLowercase.clear();
        m_LineOffSets.clear();

        int nOffSet = 0;
        for(int i = 0;i < m_Lines.size();++i) {
            int len = m_Lines.get(i).length();
            if(len > WSConfig.Max_Column_Per_Line && !m_bIsTempFile) {
                FSLog.log.warn(String.format("%s column > %d,will not be cached",file.getName(),WSConfig.Max_Column_Per_Line));
                this.m_bReadSuccess = false;
                break;
            }
            m_LinesLowercase.add(m_Lines.get(i).toLowerCase());
            m_LineOffSets.add(nOffSet);
            nOffSet += m_Lines.get(i).length() + 1;
        }
        m_virtualFile = file;
        m_fileName = file.getName();
        m_fileNameLowerCase = m_fileName.toLowerCase();
        m_timeStamp = file.getTimeStamp();
    }

    public void setDocumentModifiedTime(long time) {
        m_DocumentModifiedTime = time;
    }
    public long getDocumentModifiedTime() {
        return m_DocumentModifiedTime;
    }
}