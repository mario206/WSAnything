package WSAnything.Core;

import WSAnything.FSLog;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import org.jetbrains.annotations.NotNull;

public class WSFileListener implements VirtualFileListener,FileEditorManagerListener {
    private WSProject m_wsProject;

    public WSFileListener(WSProject pro) {
        this.m_wsProject = pro;
    }

    public void contentsChanged(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        if (!WSUtil.checkShouldCacheFile(file)) {
            return;
        }
        FSLog.log.info("contentsChanged:" + file.getName());
        WSFileCache cache = new WSFileCache();
        cache.init(file);
        m_wsProject.updateCache(file, cache);
    }

    public void fileCreated(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        if (!WSUtil.checkShouldCacheFile(file)) {
            return;
        }
        FSLog.log.info("fileCreated:" + file.getName());
        WSFileCache cache = new WSFileCache();
        cache.init(file);
        m_wsProject.addCache(file, cache,false);
    }
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        FSLog.log.info("fileDeleted:" + file.getName());
        m_wsProject.deleteCache(file);
    }

    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if(WSUtil.isTmpFile(file)) {
            FSLog.log.info("tmp file close:" + file.getName());
            m_wsProject.deleteCache(file);
        }
    }
}
