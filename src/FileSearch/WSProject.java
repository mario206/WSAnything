package FileSearch;


import com.google.common.collect.Lists;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import sun.misc.Cache;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WSProject {
    Project m_project;
    List<VirtualFile> m_solutionFile = Lists.newArrayList();
    Map<VirtualFile, WSFileCache> m_CacheFile = new HashMap<VirtualFile, WSFileCache>();

    Integer m_lock;

    AsyncTask scanFileThread = new AsyncTask((ctx)->{
        this.scanFileMain(ctx);
        return 0;
    },"scanFileThread");

    public void start(Project project) {
        synchronized (this) {
            m_project = project;
            scanFileThread.Start(new Context());
        }

    }
    public void dispose() {
        synchronized(this) {
            m_project = null;
            m_solutionFile.clear();
            m_CacheFile.clear();
        }

    }

    public void scanFileMain(Context context) {

        m_solutionFile.clear();
        m_CacheFile.clear();

        final String vecSearchSuffix[] = WSConfig.vecSearchSuffix;
        final List<String> listSearchSuffix = Arrays.asList(vecSearchSuffix);

        List<VirtualFile> solutionFile = Lists.newArrayList();
        Map<VirtualFile, WSFileCache> cacheFile = new HashMap<VirtualFile, WSFileCache>();

        ProjectRootManager.getInstance(this.m_project).getFileIndex().iterateContent(fileOrDir -> {
            if (!fileOrDir.isDirectory() && listSearchSuffix.contains(WSUtil.getFileSuffix(fileOrDir.getName()))) {
                try {
                    String text = LoadTextUtil.loadText(fileOrDir).toString();
                    WSFileCache cache = new WSFileCache();
                    cache.init(fileOrDir);

                    solutionFile.add(fileOrDir);
                    cacheFile.put(fileOrDir,cache);
                    FSLog.log.info(text);
                } catch (Exception e) {

                }
            }
            return true;
        });

        synchronized(this) {
            m_solutionFile = solutionFile;
            m_CacheFile = cacheFile;
        }
    }
}

