package WSAnything.actions;

import WSAnything.FSLog;
import WSAnything.WSFindPopupPanel;
import WSAnything.Core.WSProjectListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

public class OpenFileSearch extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        FSLog.log.info("Action performed.");
        Project project = e.getProject();

        if(project != null) {
            WSProjectListener.getInstance().projectOpened(project);
        }
        WSProjectListener.getInstance().beforeUIShow();
        WSFindPopupPanel pnl = new WSFindPopupPanel(project);
        pnl.showUI();
    }
}
