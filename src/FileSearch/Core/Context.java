package FileSearch.Core;

public class Context {
    volatile  boolean  m_bIsCancel = false;
    Object m_arg = null;

    public Context(Object arg) {
        m_arg = arg;
    }
    public Object getArg() {return m_arg;}

    public void cancelTask() {
        m_bIsCancel = true;
    }
    public boolean isTaskCanceled() {
        return m_bIsCancel;
    }
}