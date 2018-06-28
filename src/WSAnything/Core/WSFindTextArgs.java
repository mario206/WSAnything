package WSAnything.Core;

import java.util.ArrayList;
import java.util.List;

public class WSFindTextArgs {
    public FindTextRequest req;
    public List<WSFindTextResult> listResult = new ArrayList<WSFindTextResult>();
    public int m_currResultCnt = 0;
    public int activeThreadCnt;
    public List<List<WSFindTextResult>> llistResult = null;
}

