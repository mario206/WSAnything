package FileSearch.Core;


import FileSearch.FSLog;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import sun.rmi.runtime.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WSProject {
    private Project m_project;
    private WSFileListener m_FileListener = new WSFileListener(this);

    private List<VirtualFile> m_solutionFile = Lists.newArrayList();
    private Map<VirtualFile, WSFileCache> m_CacheFile = new HashMap<VirtualFile, WSFileCache>();
    private AsyncTask scanFileThread = new AsyncTask((ctx)->{
        this.scanFileMain(ctx);
        return 0;
    },"scanFileThread");

    WSProject() {
        VirtualFileManager.getInstance().addVirtualFileListener(m_FileListener);
    }
    public void start(Project project) {
        synchronized (this) {
            m_project = project;
            scanFileThread.Start(new Context(null));
        }

    }
    public void dispose() {
        synchronized(this) {
            m_project = null;
            m_solutionFile.clear();
            m_CacheFile.clear();
            VirtualFileManager.getInstance().removeVirtualFileListener(m_FileListener);
        }

    }
    public List<VirtualFile> getSolutionFileCopy() {
        return Lists.newArrayList(m_solutionFile);
    }

    public WSFileCache getCache(VirtualFile file) {
        synchronized (this) {
            WSFileCache ret = m_CacheFile.get(file);
            if(ret == null) {
                FSLog.log.error("[getCache] " + file.getName());
            }
            return ret;
        }
    }
    public void scanFileMain(Context context) {

        FSLog.log.info("scanFileMain begin");
        m_solutionFile.clear();
        m_CacheFile.clear();

        List<VirtualFile> solutionFile = Lists.newArrayList();
        Map<VirtualFile, WSFileCache> cacheFile = new HashMap<>();

        ProjectFileIndex indexer = ProjectRootManager.getInstance(this.m_project).getFileIndex();
        List<VirtualFile> fileToScan = Lists.newArrayList();

        indexer.iterateContent(fileOrDir -> {
            fileToScan.add(fileOrDir);
            return true;
        });
        FSLog.log.info("scanFileMain fileToScan = " + fileToScan.size());

        int lastProgress = 0;

        for(int i = 0;i < fileToScan.size();++i) {
            VirtualFile fileOrDir = fileToScan.get(i);
            if (WSUtil.checkShouldCacheFile(fileOrDir) && !indexer.isExcluded(fileOrDir)) {
                try {
                    //String text = LoadTextUtil.loadText(fileOrDir).toString();
                    WSFileCache cache = new WSFileCache();
                    cache.init(fileOrDir);
                    if(cache.getIsSizeValid() && cache.isReadSuccess()) {
                        //FSLog.log.info(fileOrDir.getName());
                        solutionFile.add(fileOrDir);
                        cacheFile.put(fileOrDir,cache);
                    }
                    //FSLog.log.info(text);
                } catch (Exception e) {
                    FSLog.log.error(e);
                }
            }
            int nCurrProgress = (i * 100 / fileToScan.size());
            if(nCurrProgress - lastProgress > 5) {
                lastProgress = nCurrProgress;
                FSLog.log.info("scanFileMain progress = " + nCurrProgress + "%");
            }
        }

        synchronized(this) {
            m_solutionFile = solutionFile;
            m_CacheFile = cacheFile;
        }
        FSLog.log.info("scanFileMain end,fileNums = " + solutionFile.size());
    }

    public void updateCache(VirtualFile file,WSFileCache cache) {
        synchronized (this) {
            if(m_CacheFile.get(file) != null) {
                FSLog.log.info("updateCache file:" + file.getName());
                if(cache.getIsSizeValid() && cache.isReadSuccess()) {
                    m_CacheFile.put(file,cache);
                } else {
                    FSLog.log.error(cache.m_fileName + "is size invalid,will remove");
                    m_CacheFile.remove(file);
                    m_solutionFile.remove(file);
                }
            } else {
                FSLog.log.warn("updateCache can't find file:" + file.getName());
            }
        }
    }
    public void addCache(VirtualFile file,WSFileCache cache) {
        synchronized (this) {
            if(m_CacheFile.get(file) == null) {
                if(cache.getIsSizeValid() && cache.isReadSuccess()) {
                    FSLog.log.info("addCache file:" + file.getName());
                    m_CacheFile.put(file,cache);
                    m_solutionFile.add(file);
                }
            } else {
                FSLog.log.warn("addCache file already exist" + file.getName());
            }
        }
    }
    public void deleteCache(VirtualFile file) {
        synchronized (this) {
            if(m_CacheFile.get(file) != null) {
                FSLog.log.info("do delete file:" + file.getName());
                m_CacheFile.remove(file);
                m_solutionFile.remove(file);
            }
        }
    }

    public void processUnSaveDocument() {
        final FileDocumentManager manager = FileDocumentManager.getInstance();
        for (Document document : manager.getUnsavedDocuments()) {
            VirtualFile file = manager.getFile(document);
            synchronized (this) {
                WSFileCache cache = m_CacheFile.get(file);
                if(cache != null) {
                    cache.updateFromUnSaveDocument(file,document);
                }
            }

        }
    }

}

