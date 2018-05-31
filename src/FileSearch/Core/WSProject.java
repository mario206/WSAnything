package FileSearch.Core;


import FileSearch.FSLog;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WSProject {
    private Project m_project;
    private List<VirtualFile> m_solutionFile = Lists.newArrayList();
    private Map<VirtualFile, WSFileCache> m_CacheFile = new HashMap<VirtualFile, WSFileCache>();
    private AsyncTask scanFileThread = new AsyncTask((ctx)->{
        this.scanFileMain(ctx);
        return 0;
    },"scanFileThread");
    private static WSProject pInstance;

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

        final String vecSearchSuffix[] = WSConfig.vecSearchSuffix;
        final List<String> listSearchSuffix = Arrays.asList(vecSearchSuffix);

        List<VirtualFile> solutionFile = Lists.newArrayList();
        Map<VirtualFile, WSFileCache> cacheFile = new HashMap<VirtualFile, WSFileCache>();

        ProjectRootManager.getInstance(this.m_project).getFileIndex().iterateContent(fileOrDir -> {
            if (!fileOrDir.isDirectory() && listSearchSuffix.contains(WSUtil.getFileSuffix(fileOrDir.getName()))) {
                try {
                    //String text = LoadTextUtil.loadText(fileOrDir).toString();
                    WSFileCache cache = new WSFileCache();
                    cache.init(fileOrDir);

                    solutionFile.add(fileOrDir);
                    cacheFile.put(fileOrDir,cache);
                    //FSLog.log.info(text);
                } catch (Exception e) {
                    FSLog.log.error("scanFileMain Exception");
                }
            }
            return true;
        });

        synchronized(this) {
            m_solutionFile = solutionFile;
            m_CacheFile = cacheFile;
        }
        FSLog.log.info("scanFileMain end,fileNums = " + solutionFile.size());
    }
}

