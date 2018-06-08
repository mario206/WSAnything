package FileSearch.Core;


import FileSearch.FSLog;
import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Pair;
import com.siyeh.ig.dataflow.ReuseOfLocalVariableInspection;

import java.util.ArrayList;
import java.util.List;

public class WSTextFinder {
    private static WSTextFinder pInstance;

    private int MAX_SEARCH_THREAD = Math.max(1,Runtime.getRuntime().availableProcessors() - 1);

    private List<AsyncTask> m_FindTextThreads = Lists.newArrayList();
    private AsyncTask m_FindTask = null;
    private FindTextRequest request = null;

    public static synchronized WSTextFinder getInstance( ) {
        if (pInstance == null) {
            pInstance = new WSTextFinder();
            pInstance.createWorkers();
        }
        return pInstance;
    }
    public  void createWorkers() {
        if(m_FindTextThreads.size() != MAX_SEARCH_THREAD) {
            for(int i = 0;i < MAX_SEARCH_THREAD;++i) {
                AsyncTask worker = new AsyncTask((ctx)->{
                    this.findTextThreadMain(ctx);
                    return 0;
                },"FindTextThread_" + i);
                m_FindTextThreads.add(worker);
            }
        }
        if(m_FindTask == null) {
            m_FindTask = new AsyncTask((ctx)-> {
                this.findTask(ctx);
                return 0;
            },"FindTextTask");
        }
    }
    public void start(FindTextRequest req) {
        FSLog.log.info("start req ");
        m_FindTask.Start(new Context(req));
    }
    public void dispose() {
        //todo
    }

    public void findTask(Context context) {

        WSProjectListener.getInstance().getWSProject().processUnSaveDocument();

        FindTextRequest req = (FindTextRequest)context.getArg();
        if(req.m_TextBoxText.isEmpty()) {
            FSLog.log.info("search pattern empty,return");
            return;
        }
        if(req.m_searchFiles.isEmpty()) {
            FSLog.log.info("m_searchFiles empty,return");
            return;
        }

        FSLog.log.info(String.format("[%d]search text:[%s]", req.m_nTag,req.m_TextBoxText));
        FSLog.log.info("searchFileNums = " + req.m_searchFiles.size());
        WSFindTextArgs args = new WSFindTextArgs();
        args.req = req;
        args.activeThreadCnt = MAX_SEARCH_THREAD;

        for(int i = 0;i < MAX_SEARCH_THREAD;++i) {
            m_FindTextThreads.get(i).Start(new Context(args));
        }

        /// wait for all thread finish
        synchronized (args) {
            while (args.activeThreadCnt != 0) {
                try {
                    FSLog.log.info("wait for threadCnt = " + args.activeThreadCnt);
                    args.wait();
                } catch(Exception e) {
                    FSLog.log.error("wait for text search thread Exception");
                }
            }
        }
        FSLog.log.info("find Task finish");
        req.m_finishCallBack.apply(args);
    }

    public void findTextThreadMain(Context context) {
        FSLog.log.info("findTextThreadMain begin");
        WSFindTextArgs args = (WSFindTextArgs) context.getArg();
        List<WSFindTextResult> result = new ArrayList<WSFindTextResult>();
        VirtualFile file = null;
        while (true) {
            synchronized (args) {
                if(context.isTaskCanceled()) {
                    FSLog.log.info("findTextThreadMain cancel");
                    break;
                }
                if(args.req.m_currIndex < args.req.m_searchFiles.size()) {
                    if(args.req.m_nMaxResult <= args.m_currResultCnt) {
                        args.req.m_upToResultCntLimit = true;
                        break;
                    }
                    file = args.req.m_searchFiles.get(args.req.m_currIndex++);
                    //FSLog.log.info("search File" + file.getName())
                } else {
                    FSLog.log.info("search file empty,break");
                    break;
                }
            }
            if(file != null) {
                List<WSFindTextResult> tmpRsult = searchFile(file,args.req);
                if(tmpRsult != null) {
                    result.addAll(tmpRsult);
                    if(tmpRsult.size() > 0) {
                        synchronized (args) {
                            args.req.m_nResultFileCnt++;
                            args.m_currResultCnt += tmpRsult.size();
                        }
                    }
                }
            }

        }
        FSLog.log.info("findTextThreadMain end");
        synchronized(args) {
            if(result != null) {
                args.listResult.addAll(result);
            }
            args.activeThreadCnt--;
            args.notify();
        }
    }

    public static List<WSFindTextResult> searchFile(VirtualFile file, FindTextRequest req) {
        List<WSFindTextResult> result = null;
        WSFileCache cache = WSProjectListener.getInstance().getWSProject().getCache(file);
        for(int i = 0;i < cache.m_LinesLowercase.size();++i) {
            String line = cache.m_LinesLowercase cla .get(i);

            WSFindTextResult oneLineResult = searchLine(i, line, file, req, cache);
            if (oneLineResult != null) {
                if(result == null)  result = new ArrayList<>();
                result.add(oneLineResult);
            }
        }
        return result;
    }

    public static WSFindTextResult searchLine(int nLineNum,String line,VirtualFile file,FindTextRequest req,WSFileCache cache) {
        WSFindTextResult result = null;

        FindTextRequest.Pattern pattern = req.m_pattern;
        List<Pair<Integer,Integer>> listMatchIndexs = null;

        int nWordMatchCount = 0;
        boolean bAllMatch = true;
        int startIndex = -1;
        int endIndex = -1;

        for(int j = 0;j < pattern.vec.length && bAllMatch;++j) {
            String word = pattern.vec[j];
            int index = startIndex = line.indexOf(word);
            if(index != -1) {
                // pattern[j] match
                nWordMatchCount++;
                startIndex = index;
                endIndex = index + word.length() - 1;
                if(listMatchIndexs == null) listMatchIndexs = new ArrayList<>();
                listMatchIndexs.add(new Pair(index,endIndex));
            } else {
                // not found
                bAllMatch = false;
                break;
            }
        }
        if(bAllMatch) {
            WSFindTextResult oneLineResult = new WSFindTextResult();
            oneLineResult.m_virtualFile = file;
            oneLineResult.m_nLineIndex = nLineNum;
            oneLineResult.nBeginIndex = startIndex;
            oneLineResult.nEndIdex = endIndex;
            oneLineResult.m_strLineLowercase = line;
            oneLineResult.m_strLine = cache.m_Lines.get(nLineNum);
            oneLineResult.m_nLineOffset = cache.m_LineOffSets.get(nLineNum);
            oneLineResult.m_ListMatchTextIndexs = listMatchIndexs;
            result = oneLineResult;
        }
        return result;
    }

}


