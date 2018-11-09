package WSAnything.Core;


import WSAnything.FSLog;
import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Pair;
import sun.misc.Cache;

import java.util.ArrayList;
import java.util.List;

public class WSTextFinder {
    private static WSTextFinder pInstance;
    private List<WSFindTextResult> m_lastResult = new ArrayList<>();

    private int MAX_SEARCH_THREAD = Math.max(1,Runtime.getRuntime().availableProcessors() - 1);

    private List<AsyncTask> m_FindTextThreads = Lists.newArrayList();
    private AsyncTask m_FindTask = null;

    public static synchronized WSTextFinder getInstance( ) {
        if (pInstance == null) {
            pInstance = new WSTextFinder();
            pInstance.createWorkers();
        }
        return pInstance;
    }
    public void beforeUIShow() {
        m_lastResult = new ArrayList<>();
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
        WSProject project = WSProjectListener.getInstance().getWSProject();
        if(project == null || !project.isReady()) {
            return;
        }
        project.processUnSaveDocument();
        project.processTemporaryDocument();

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
        args.llistResult = new ArrayList<>(args.req.m_searchFiles.size());
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
                    FSLog.log.warn("wait for text search thread Exception");
                }
            }
        }
        FSLog.log.info("find Task finish");
        for(int i = 0;i < args.llistResult.size();++i) {
            List<WSFindTextResult> list = args.llistResult.get(i);
            if(list != null) {
                args.listResult.addAll(list);
            }
        }
        if(args.listResult.size() > req.m_nMaxResult) {
            args.listResult = args.listResult.subList(0,req.m_nMaxResult);
        }
        m_lastResult = args.listResult;
        req.m_finishCallBack.apply(args);
    }

    public void findTextThreadMain(Context context) {
        FSLog.log.info("findTextThreadMain begin");
        WSFindTextArgs args = (WSFindTextArgs) context.getArg();
        VirtualFile file = null;
        while (true) {
            int currIndex = -1;
            synchronized (args) {
                currIndex = args.req.m_currIndex;
                if(context.isTaskCanceled()) {
                    FSLog.log.info("findTextThreadMain cancel");
                    break;
                }
                if(args.req.m_currIndex < args.req.m_searchFiles.size()) {
                    if(args.req.m_nMaxResult <= args.m_currResultCnt) {
                        args.req.m_upToResultCntLimit = true;
                        break;
                    }
                    file = args.req.m_searchFiles.get(currIndex);
                    args.req.m_currIndex++;
                    //FSLog.log.info("search File" + file.getName())
                } else {
                    FSLog.log.info("search file empty,break");
                    break;
                }
            }
            if(file != null) {
                List<WSFindTextResult> tmpRsult = null;
                try {
                    tmpRsult = searchFile(file,args.req);
                }catch (Exception e) {
                    FSLog.log.warn(e);
                }
                if(tmpRsult != null && tmpRsult.size() > 0) {
                    synchronized (args) {
                        args.req.m_nResultFileCnt++;
                        args.m_currResultCnt += tmpRsult.size();
                        try {
                            while(args.llistResult.size() < currIndex) {
                                args.llistResult.add(null);
                            }
                            args.llistResult.add(currIndex, tmpRsult);
                        } catch (Exception e) {
                            FSLog.log.info("sd");
                        }
                    }
                }
            }
        }
        FSLog.log.info("findTextThreadMain end");
        synchronized(args) {
            args.activeThreadCnt--;
            args.notify();
        }
    }

    public static List<WSFindTextResult> searchFile(VirtualFile file, FindTextRequest req) {
        List<WSFindTextResult> result = null;
        WSFileCache cache = WSProjectListener.getInstance().getWSProject().getCache(file);
        if(cache != null) {
            for(int i = 0;i < cache.m_Lines.size();++i) {
                String line = cache.m_Lines.get(i).toLowerCase();
                WSFindTextResult oneLineResult = searchLine(i, line, file, req, cache);
                if (oneLineResult != null) {
                    if(result == null)  result = new ArrayList<>();
                    result.add(oneLineResult);
                }
            }
        }
        return result;
    }

    public static WSFindTextResult searchLine(int nLineNum,String line,VirtualFile file,FindTextRequest req,WSFileCache cache) {
        WSFindTextResult result = null;

        FindTextRequest.Pattern pattern = req.m_pattern;
        String fileName = cache.m_fileName;
        String fileNamelowercase = cache.m_fileNameLowerCase;
        List<Pair<Integer,Integer>> listMatchTextIndexs = null;
        List<Pair<Integer,Integer>> listMatchFileIndexs = null;


        int nWordMatchCount = 0;
        int nFileMatchCount = 0;
        boolean bAllMatch = true;
        int startIndex = -1;
        int endIndex = -1;

        for(int j = 0;j < pattern.vec.length && bAllMatch;++j) {
            String word = pattern.vec[j];
            int index = startIndex = line.indexOf(word);

            if(index != -1) {
                // the word is matched!
                nWordMatchCount++;
                startIndex = index;
                endIndex = index + word.length() - 1;
                if(listMatchTextIndexs == null) listMatchTextIndexs = new ArrayList<>();
                listMatchTextIndexs.add(new Pair(index,endIndex));
            } else {
                if(!req.m_bConsiderFileName) {
                    // no chance !
                    bAllMatch = false;
                    break;
                } else {
                    /// try if pattern[j] match fileName
                    int fIndex = fileNamelowercase.indexOf(word);
                    if(fIndex != -1) {
                        /// success to match the file name !!!
                        nFileMatchCount++;
                        if(listMatchFileIndexs == null) listMatchFileIndexs = new ArrayList<>();
                        listMatchFileIndexs.add(new Pair<>(fIndex,fIndex + word.length() -1));
                    } else {
                        bAllMatch = false;
                        break;
                    }
                }

            }
        }
        if(bAllMatch && nWordMatchCount > 0) {
            int cnt = listMatchTextIndexs.size();

            WSFindTextResult oneLineResult = new WSFindTextResult();
            oneLineResult.m_virtualFile = file;
            oneLineResult.m_nLineIndex = nLineNum;
            oneLineResult.nBeginIndex = listMatchTextIndexs.get(cnt - 1).first;
            oneLineResult.nEndIdex = listMatchTextIndexs.get(cnt - 1).second;
            oneLineResult.m_strLineLowercase = line;
            oneLineResult.m_strLine = cache.m_Lines.get(nLineNum);
            oneLineResult.m_nLineOffset = cache.m_LineOffSets.get(nLineNum);
            oneLineResult.m_ListMatchTextIndexs = listMatchTextIndexs;
            oneLineResult.m_ListMatchFileNameIndex = listMatchFileIndexs;
            oneLineResult.m_strFileName = fileName;
            oneLineResult.m_bConsiderFileName = nFileMatchCount > 0;
            result = oneLineResult;
        }
        return result;
    }

    public List<WSFindTextResult> getLastResult() {
        return this.m_lastResult;
    }

}


