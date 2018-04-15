package FileSearch;

public class Context {
    Object obj;
    volatile  boolean  m_bIsCancel = false;

    public Context() {

    }
    public void cancelTask() {
        m_bIsCancel = true;
    }
    public boolean isTaskCanceled() {
        return m_bIsCancel;
    }
}