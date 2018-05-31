package FileSearch.Core;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class WSFindTextArgs {
    public FindTextRequest req;
    public List<WSFindTextResult> listResult = new ArrayList<WSFindTextResult>();
    public int m_currResultCnt = 0;
    public int activeThreadCnt;
}

