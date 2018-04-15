package FileSearch;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;



public class AsyncTask {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    Context m_context = null;
    boolean m_bQuit = false;
    Integer m_lock;
    Function<Context,Integer> m_threadMain;
    String m_threadName;

    public AsyncTask(Function<Context,Integer> threadMain,String threadName) {
        m_threadMain = threadMain;
        m_threadName = threadName;
    }

    public void Start(Context context) {
        synchronized(m_lock) {
            if(m_context != null) {
                m_context.cancelTask();
            }
            m_context = context;
        }
        m_lock.notifyAll();
        ///wait for thread finish

    }
    public void init() {
        executor.submit(() -> {
            while(!m_bQuit) {

                Context newContext = null;
                //// try to get new context
                while(true) {
                    synchronized(m_lock) {
                        newContext = m_context;
                        if(newContext == null) {
                            try {
                                m_lock.wait();
                            } catch (Exception ex) {
                                System.out.println("AsyncTask init m_lock ex");
                            }
                        } else {
                            break;
                        }
                    }
                }
                //// here we've got a new context,Run Function
                System.out.println(m_threadName + " Run");
                m_threadMain.apply(newContext);
            }
        });
    }
}
