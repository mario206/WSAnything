package FileSearch.Core;


import FileSearch.FSLog;
import com.google.common.collect.Lists;
import com.intellij.ide.ui.EditorOptionsTopHitProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import org.apache.velocity.runtime.directive.Foreach;
import org.codehaus.groovy.runtime.metaclass.MetaMethodIndex;
import sun.rmi.runtime.Log;

import java.util.*;

public class WSProject {
    private Project m_project;
    private WSFileListener m_FileListener = new WSFileListener(this);

    private List<WSSolutionFile> m_solutionFile = Lists.newArrayList();
    private Map<VirtualFile, WSFileCache> m_CacheFile = new HashMap<VirtualFile, WSFileCache>();
    private long m_nFilesNums = 0;
    private long m_nLineNums = 0;
    private WSEventListener m_Listener = null;
    private boolean m_bIsReady = false;


    private AsyncTask scanFileThread = new AsyncTask((ctx) -> {
        this.scanFileMain(ctx);
        return 0;
    }, "scanFileThread");

    WSProject() {
    }

    public void registerListener() {
        VirtualFileManager.getInstance().addVirtualFileListener(m_FileListener);
        FileEditorManager.getInstance(this.m_project).addFileEditorManagerListener(this.m_FileListener);
    }
    public void start(Project project) {
        synchronized (this) {
            m_project = project;
            scanFileThread.Start(new Context(null));
        }
        registerListener();
    }

    public void close() {
        FSLog.log.info(String.format("Project %s close", m_project.getName()));
        List<WSFileCache> list = new ArrayList<>(m_CacheFile.values());
        //CacheFileSerializer.SerializeToLocalFile(m_project,list);
    }

    public void dispose() {
        synchronized (this) {
            VirtualFileManager.getInstance().removeVirtualFileListener(m_FileListener);
            FileEditorManager.getInstance(this.m_project).removeFileEditorManagerListener(this.m_FileListener);
            m_project = null;
            m_solutionFile.clear();
            m_CacheFile.clear();
            m_FileListener = null;
        }
        System.gc();
        System.runFinalization();

    }

    public boolean isReady() {
        return this.m_bIsReady;
    }

    public List<VirtualFile> getCurrFileCopy() {
        List<VirtualFile> ret = new ArrayList<>();
        try {
            PsiFile file = WSUtil.getSelectedEditorPsiFile();
            ret.add(file.getVirtualFile());
        } catch (Exception e) {

        }
        return ret;
    }

    public List<VirtualFile> getSolutionFileCopy() {
        //return Lists.newArrayList(m_solutionFile);
        List<VirtualFile> ret = new ArrayList<>();
        ProjectFileIndex indexer = ProjectRootManager.getInstance(this.m_project).getFileIndex();
        synchronized (this) {
            for (int i = 0; i < m_solutionFile.size(); ++i) {
                WSSolutionFile solFile = m_solutionFile.get(i);
                if (!solFile.m_bIsTempFile && !indexer.isExcluded(solFile.m_virtualFile)) {
                    ret.add(solFile.m_virtualFile);
                }
            }
        }
        return ret;
    }

    public WSFileCache getCache(VirtualFile file) {
        synchronized (this) {
            WSFileCache ret = m_CacheFile.get(file);
            if (ret == null) {
                FSLog.log.error("[getCache] " + file.getName());
            }
            return ret;
        }
    }

    public void scanFileMain(Context context) {

        FSLog.log.info("scanFileMain begin");
        m_solutionFile.clear();
        m_CacheFile.clear();

        //Map<String, WSFileCache> cacheMap = CacheFileSerializer.readFromLocalFile(m_project);
        Map<String, WSFileCache> cacheMap = new HashMap<>();

        List<WSSolutionFile> solutionFile = Lists.newArrayList();
        Map<VirtualFile, WSFileCache> cacheFile = new HashMap<>();

        ProjectFileIndex indexer = ProjectRootManager.getInstance(this.m_project).getFileIndex();
        List<VirtualFile> fileToScan = Lists.newArrayList();

        indexer.iterateContent(fileOrDir -> {
            fileToScan.add(fileOrDir);
            return true;
        });
        FSLog.log.info("scanFileMain fileToScan = " + fileToScan.size());
        int lastProgress = 0;

        for (int i = 0; i < fileToScan.size(); ++i) {
            VirtualFile fileOrDir = fileToScan.get(i);
            if (WSUtil.checkShouldCacheFile(fileOrDir) && !indexer.isExcluded(fileOrDir)) {
                try {
                    WSFileCache cache = cacheMap.get(fileOrDir.getName());
                    if (cache == null) {
                        cache = new WSFileCache();
                        cache.init(fileOrDir);
                    } else {
                        cache.updateFromVirtualFile(fileOrDir);
                    }
                    if (cache.isReadSuccess()) {
                        solutionFile.add(new WSSolutionFile(fileOrDir, false));
                        cacheFile.put(fileOrDir, cache);
                    }
                    //FSLog.log.info(text);
                } catch (Exception e) {
                    FSLog.log.error(e);
                }
            }
            int nCurrProgress = (i * 100 / fileToScan.size());
            if (nCurrProgress - lastProgress > 5) {
                lastProgress = nCurrProgress;
                FSLog.log.info("scanFileMain progress = " + nCurrProgress + "%");
            }
        }

        synchronized (this) {
            m_solutionFile = solutionFile;
            m_CacheFile = cacheFile;
            this.calculateFileLineNums();
        }
        m_bIsReady = true;
        System.gc();
        System.runFinalization();

        if (m_Listener != null) {
            m_Listener.onFileCacheFinish();
        }
        FSLog.log.info(String.format("scanFileMain end,file = %d,lines = %d", this.m_nFilesNums, this.m_nLineNums));
    }

    public void updateCache(VirtualFile file, WSFileCache cache) {
        synchronized (this) {
            if (m_CacheFile.get(file) != null) {
                FSLog.log.info("updateCache file:" + file.getName());
                if (cache.isReadSuccess()) {
                    m_CacheFile.put(file, cache);
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

    public void addOrUpdateTempCache(VirtualFile file, WSFileCache cache) {
        if (cache == null) {
            FSLog.log.info("add temp file name = " + file.getName());
            cache = new WSFileCache();
            cache.setIsTmpFile();
        }
        cache.init(file);
        this.addCache(file, cache, true);
    }

    public void addCache(VirtualFile file, WSFileCache cache, boolean isTmpFile) {
        synchronized (this) {
            if (m_CacheFile.get(file) == null) {
                if (cache.isReadSuccess()) {
                    FSLog.log.info("addCache file:" + file.getName());
                    m_CacheFile.put(file, cache);
                    m_solutionFile.add(new WSSolutionFile(file, isTmpFile));
                }
            } else {
                FSLog.log.warn("addCache file already exist" + file.getName());
            }
        }
    }

    public void deleteCache(VirtualFile file) {
        synchronized (this) {
            if (m_CacheFile.get(file) != null) {
                FSLog.log.info("do delete file:" + file.getName());
                m_CacheFile.remove(file);
                m_solutionFile.remove(file);
            }
        }
    }

    public void processUnSaveDocument() {
        try {
            final FileDocumentManager manager = FileDocumentManager.getInstance();
            for (Document document : manager.getUnsavedDocuments()) {
                VirtualFile file = manager.getFile(document);
                synchronized (this) {
                    WSFileCache cache = m_CacheFile.get(file);
                    if (cache != null) {
                        cache.updateFromUnSaveDocument(file, document);
                    }
                }
            }
        } catch (Exception e) {
            FSLog.log.error(e);
        }
    }
    /// these documents are not in project
    public void processTemporaryDocument() {
        VirtualFile[] files = FileEditorManager.getInstance(WSProjectListener.getInstance().getJBProject()).getOpenFiles();
        for (int i = 0; i < files.length; ++i) {
            VirtualFile file = files[i];
            try {
                synchronized (this) {
                    WSFileCache cache = m_CacheFile.get(file);
                    if (cache == null || cache.getIsTmpFile()) {
                        WSProjectListener.getInstance().getWSProject().addOrUpdateTempCache(file, cache);
                    }
                }
            } catch (Exception e) {
                FSLog.log.error(e);
            }

        }

    }
    public boolean isFileTmpFile(VirtualFile file) {
        boolean ret = false;
        if (m_bIsReady) {
            try {
                synchronized (this) {
                    WSFileCache cache = m_CacheFile.get(file);
                    if (cache == null || cache.getIsTmpFile()) {
                        ret = true;
                    }
                }
            } catch (Exception e) {
                FSLog.log.error(e);
            }
        }
        return ret;
    }

    private void calculateFileLineNums() {
        this.m_nLineNums = 0;
        m_CacheFile.forEach((k, v) -> {
            this.m_nLineNums += v.m_Lines.size();
        });
        this.m_nFilesNums = m_CacheFile.size();
    }

    public long getFileNums() {
        return this.m_nFilesNums;
    }

    public long getLineNums() {
        return this.m_nLineNums;
    }

    public void registerEventListener(WSEventListener e) {
        m_Listener = e;
    }

    public void unRegisterEventListener(WSEventListener e) {
        if (m_Listener == e) {
            m_Listener = null;
        }
    }
}

