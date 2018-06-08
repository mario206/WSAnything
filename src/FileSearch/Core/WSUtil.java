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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

            //int beginOffset = StringUtil.lineColToOffset(document.getCharsSequence(),result.m_nLineIndex,result.nBeginIndex);
            //int endOffset = StringUtil.lineColToOffset(document.getCharsSequence(),result.m_nLineIndex,result.nEndIdex + 1);


            int beginOffset = result.m_nLineOffset + result.nBeginIndex;
            int endOffset = result.m_nLineOffset + result.nEndIdex + 1;

            info = ReadAction.compute(() -> {
                UsageInfo tmp_info = new UsageInfo(psiFile,beginOffset,endOffset,false);
                return tmp_info;
            });
        } catch (Exception e) {
            return null;
        }
        return info;
    }


    public static List<UsageInfo> getUsageInfoList(WSFindTextResult result) {

        List<UsageInfo> list = new ArrayList<>();

        for(int i = 0; i < result.m_ListMatchTextIndexs.size(); ++i) {
            UsageInfo info = null;
            Pair<Integer,Integer> indexPair = result.m_ListMatchTextIndexs.get(i);

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
                int beginOffset = result.m_nLineOffset + indexPair.first;
                int endOffset = result.m_nLineOffset + indexPair.second + 1;

                info = ReadAction.compute(() -> {
                    UsageInfo tmp_info = new UsageInfo(psiFile,beginOffset,endOffset,false);
                    return tmp_info;
                });
            } catch (Exception e) {
                FSLog.log.info(e);
            }

            if(info != null){
                list.add(info);
            }
        }
        return list;
    }

    public static UsageInfo2UsageAdapter getMergedUsageAdapter(WSFindTextResult result) {
        List<UsageInfo> list = getUsageInfoList(result);
        UsageInfo2UsageAdapter adapter = null;
        for(int i = 0;i < list.size();++i) {
            UsageInfo2UsageAdapter tmp = new UsageInfo2UsageAdapter(list.get(i));
            try {
                if(adapter == null) {
                    adapter = tmp;
                } else {
                    adapter.merge(tmp);
                }
            }catch (Exception e) {
                FSLog.log.info(e);
            }

        }
        return adapter;
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
        Project pro = WSProjectListener.getInstance().getJBProject();
        File ideaFloderPath = new File(pro.getBasePath(),pro.DIRECTORY_STORE_FOLDER);

        if (!file.isDirectory()
            && !FileUtil.isAncestor(ideaFloderPath,new File(file.getPath()),false)
            && WSConfig.ListSearchSuffix.contains(WSUtil.getFileSuffix(file.getName()))
            ) {
            VirtualFile ProjectDir = pro.getBaseDir();
//            FSLog.log.info(pro.getBasePath());
//            FSLog.log.info(pro.getProjectFile().getName());
//            FSLog.log.info(pro.getWorkspaceFile().getName());

            if (!VfsUtilCore.isAncestor(ProjectDir, file, false)) {
                //FSLog.log.warn(file.getName() + " is not under Project");
                return false;
            }
            return true;
        }
        return false;
    }
}
