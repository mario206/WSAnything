package FileSearch.Core;
import FileSearch.FSLog;
import FileSearch.WSFindPopupPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class WSProjectListener implements VetoableProjectManagerListener {

    private static WSProjectListener pInstance;
    private Project m_currProject;
    private WSProject m_wsProject;

    public void registerEvent() {
        ProjectManager.getInstance().addProjectManagerListener(this);
    }

    public static synchronized WSProjectListener getInstance( ) {
        if (pInstance == null)
            pInstance = new WSProjectListener();
        return pInstance;
    }

    public boolean canClose(@NotNull Project project) {
        return true;
    }

    public void projectOpened(Project project) {
        FSLog.log.info("projectOpened" + project.getName());
        startProject(project);
    }
    public void projectClosed(Project project) {
        FSLog.log.info("projectClosed" + project.getName());
        if(m_wsProject != null) {
            m_wsProject.close();
            m_wsProject.dispose();
            m_wsProject = null;
        }
    }

    public void projectClosing(Project project) {
        FSLog.log.info("projectClosing" + project.getName());
    }

    public void startProject(Project pro) {
        if(pro != m_currProject) {
            FSLog.log.info("start new project: " + pro.getName());
            m_currProject = pro;
            if(m_wsProject != null) {
                m_wsProject.close();
                m_wsProject.dispose();
                m_wsProject = null;
            }
            m_wsProject = new WSProject();
            m_wsProject.start(pro);
        }
    }
    public WSProject getWSProject() {
        return m_wsProject;
    }

    public Project getJBProject() {return m_currProject;}
}
