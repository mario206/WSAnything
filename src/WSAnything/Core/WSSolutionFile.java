package WSAnything.Core;

import com.intellij.openapi.vfs.VirtualFile;

public class WSSolutionFile {
    WSSolutionFile(VirtualFile file,boolean isTempFile) {
        m_virtualFile = file;
        m_bIsTempFile = isTempFile;
    }
    public VirtualFile m_virtualFile = null;
    public boolean m_bIsTempFile = false;
}
