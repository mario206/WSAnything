package FileSearch.Core;

import FileSearch.FSLog;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

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
        return new OpenFileDescriptor(pro, file,result.m_nLineIndex,result.nBeginIndex);
    }

    //mariotodo
    public static UsageInfo getUsageInfo(WSFindTextResult result) {
        UsageInfo info = null;
        try {
            Pair.NonNull<PsiFile, VirtualFile> pair = ReadAction.compute(() -> findFile(result.m_virtualFile));
            PsiFile psiFile = pair.first;

            Project project = psiFile.getProject();
            Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            final FileDocumentManager manager = FileDocumentManager.getInstance();

            for (Document doc : manager.getUnsavedDocuments()) {
                VirtualFile file = manager.getFile(document);
                if (file == result.m_virtualFile) {
                    document = doc;
                    break;
                }
            }
            int offSet = StringUtil.lineColToOffset(document.getCharsSequence(),result.m_nLineIndex,result.nBeginIndex);
            int endOffSet = StringUtil.lineColToOffset(document.getCharsSequence(),result.m_nLineIndex,result.nEndIdex + 1);

            info = ReadAction.compute(() -> {
                UsageInfo tmp_info = new UsageInfo(psiFile,offSet,endOffSet,false);
                return tmp_info;
            });
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

    public static String getWordAtCaret(Project project) {
        String result = "";

        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if(editor == null) {
            return result;
        }
        int caretOffset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        CharSequence text = document.getCharsSequence();
        int start = 0;
        int end = document.getTextLength();
        if (!editor.getSelectionModel().hasSelection()) {
            for (int i = caretOffset - 1; i >= 0; i--) {
                char c = text.charAt(i);
                if (!Character.isJavaIdentifierPart(c)) {
                    start = i + 1;
                    break;
                }
            }
            for (int i = caretOffset; i < document.getTextLength(); i++) {
                char c = text.charAt(i);
                if (!Character.isJavaIdentifierPart(c)) {
                    end = i;
                    break;
                }
            }
        } else {
            start = editor.getSelectionModel().getSelectionStart();
            end = editor.getSelectionModel().getSelectionEnd();
        }
        if (start >= end) {
            return result;
        }
        result = text.subSequence(start, end).toString();
        return result;
    }
    public static boolean checkShouldCacheFile(VirtualFile file) {
        if (!file.isDirectory() && WSConfig.ListSearchSuffix.contains(WSUtil.getFileSuffix(file.getName()))) {
            VirtualFile ProjectDir = WSProjectListener.getInstance().getJBProject().getBaseDir();
            if (!VfsUtilCore.isAncestor(ProjectDir, file, false)) {
                FSLog.log.warn(file.getName() + " is not under Project");
                return false;
            }
            return true;
        }
        return false;
    }
}
