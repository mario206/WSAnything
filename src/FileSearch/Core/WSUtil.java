package FileSearch.Core;

import com.intellij.find.FindModel;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WSUtil {
    public static String getFileSuffix(String fileName) {
        String suffix = "";
        int i = fileName.lastIndexOf('.');
        if (i != -1) {
            suffix = fileName.substring(i);
        }
        return suffix;
    }

    public static Editor navigateToFile(WSFindTextResult info,boolean focus) {
        return openTextEditor(info,focus);
    }

    public static Editor openTextEditor(WSFindTextResult info,boolean focus) {
        Project pro = WSProjectListener.getInstance().getJBProject();
        OpenFileDescriptor desc = getDescriptor(pro,info);
        return FileEditorManager.getInstance(pro).openTextEditor(desc, focus);
    }
    public static OpenFileDescriptor getDescriptor(Project pro,WSFindTextResult result) {
        VirtualFile file = result.m_virtualFile;
        return new OpenFileDescriptor(pro, file,result.m_nLineNum,result.nBeginIndex);
    }

    public static UsageInfo getUsageInfo(WSFindTextResult result) {
        UsageInfo info = null;
        try {
            Pair.NonNull<PsiFile, VirtualFile> pair = ReadAction.compute(() -> findFile(result.m_virtualFile));
            PsiFile psiFile = pair.first;
            VirtualFile sourceVirtualFile = pair.second;
            info = new UsageInfo(psiFile,0,0,false);
        } catch (Exception e) {
            return null;
        }
        return info;
    }
    private static Pair.NonNull<PsiFile, VirtualFile> findFile(@NotNull final VirtualFile virtualFile) {
        Project project = WSProjectListener.getInstance().getJBProject();
        PsiManager myPsiManager = PsiManager.getInstance(project);
        PsiFile psiFile = myPsiManager.findFile(virtualFile);
        if (psiFile != null) {
            PsiFile sourceFile = (PsiFile)psiFile.getNavigationElement();
            if (sourceFile != null) psiFile = sourceFile;
            if (psiFile.getFileType().isBinary()) {
                psiFile = null;
            }
        }
        VirtualFile sourceVirtualFile = PsiUtilCore.getVirtualFile(psiFile);
        if (psiFile == null || psiFile.getFileType().isBinary() || sourceVirtualFile == null) {
            return null;
        }
        return Pair.createNonNull(psiFile, sourceVirtualFile);
    }
}
