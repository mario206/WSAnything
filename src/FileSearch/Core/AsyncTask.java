package FileSearch.Core;

import FileSearch.Core.Context;
import FileSearch.FSLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;



public class AsyncTask {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    Context m_context = null;
    boolean m_bQuit = false;
    Function<Context,Integer> m_threadMain;
    String m_threadName;

    public AsyncTask(Function<Context,Integer> threadMain,String threadName) {
        m_threadMain = threadMain;
        m_threadName = threadName;
        this.init();
    }

    public void Start(Context context) {
        synchronized(this) {
            if(m_context != null) {
                m_context.cancelTask();
            }
            m_context = context;
            this.notify();
        }
        ///wait for thread finish
    }
    public void init() {
        executor.submit(() -> {
            while(!m_bQuit) {

                Context newContext = null;
                //// try to get new context
                while(true) {
                    synchronized(this) {
                        newContext = m_context;
                        if (newContext == null) {
                            try {
                                this.wait();
                            } catch (Exception ex) {
                                FSLog.log.error("AsyncTask wait exception1");
                            }
                        } else {
                            break;
                        }
                    }
                }
                //// here we've got a new context,Run Function
                FSLog.log.info(m_threadName + " Run");
                if(!newContext.isTaskCanceled()) {
                    FSLog.log.info(m_threadName + " ExcuteMain");
                    try {
                        m_threadMain.apply(newContext);
                    } catch (Exception ex) {
                        FSLog.log.error("AsyncTask wait exception2 :" + m_threadName);
                        FSLog.log.error(ex);
                    }
                } else {
                    FSLog.log.info(m_threadName + " task is cancel");
                }
                synchronized(this) {
                    if(newContext == m_context) {
                        FSLog.log.info(m_threadName + " AsyncTask is processed");
                        this.m_context = null;
                    }
                }
            }
        });
    }
}
