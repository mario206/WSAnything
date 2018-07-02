package WSAnything.Core;

import WSAnything.FSLog;
import com.intellij.execution.ExecutionException;
import com.intellij.lang.Language;
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
import com.intellij.psi.*;

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
        OpenFileDescriptor desc = getDescriptor(pro,info.m_virtualFile,info.m_nLineIndex,info.nBeginIndex);
        return FileEditorManager.getInstance(pro).openTextEditor(desc, focus);
    }
    public static Editor openTextEditor(PsiFile file) {
        Project pro = WSProjectListener.getInstance().getJBProject();
        OpenFileDescriptor desc = getDescriptor(pro,file.getVirtualFile(),0,0);
        return FileEditorManager.getInstance(pro).openTextEditor(desc, true);
    }

    public static OpenFileDescriptor getDescriptor(Project pro,VirtualFile file,int logicalLine,int logicalColumn) {
        return new OpenFileDescriptor(pro, file,logicalLine,logicalColumn);
    }

    //mariotodo
    public static UsageInfo getUsageInfo(WSFindTextResult result) {
        UsageInfo info = null;
        try {
            Pair<PsiFile, VirtualFile> pair = ReadAction.compute(() -> findFile(result.m_virtualFile));
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
                Pair<PsiFile, VirtualFile> pair = ReadAction.compute(() -> findFile(result.m_virtualFile));
                if(pair == null) {
                    continue;
                }
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



    public static Pair<PsiFile, VirtualFile> findFile(@NotNull final VirtualFile virtualFile) {
        try {
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
        } catch (Exception e) {
            return null;
        }
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
            && isSupportedSuffix(file)
            && isSupportedSize(file)
            && !isSuffixToExclude(file)
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

    public static boolean isSuffixToExclude(VirtualFile file) {
        String name = file.getName();
        for(int i = 0;i < WSConfig.ListExcludeSuffx.size();++i) {
            if(name.endsWith(WSConfig.ListExcludeSuffx.get(i))) {
                FSLog.log.info("Exclude min.js : " + name);
                return true;
            }
        }
        return false;
    }

    public static boolean isSupportedSuffix(VirtualFile file) {
        String suffix = WSUtil.getFileSuffix(file.getName());
        return WSConfig.ListSearchSuffix_ProgramLang.contains(suffix) || WSConfig.ListSearchSuffix_OtherFile.contains(suffix);
    }

    public static  boolean isSupportSize_impl(List<String> list,long maxSize,long size,String suffix,String fileName,boolean bLog) {
        if(list.contains(suffix)) {
            if(size <= maxSize) {
                return true;
            } else {
                if(bLog) {
                    FSLog.log.info(String.format("%s(%d byte) is bigger than %d,will no be cached",fileName,size,maxSize));
                }
                return false;
            }
        }
        return false;
    }

    public static boolean isSupportedSize(VirtualFile file) {
        String name = file.getName();
        String suffix = WSUtil.getFileSuffix(name);
        long size = file.getLength();

        return isSupportSize_impl(WSConfig.ListSearchSuffix_ProgramLang,WSConfig.MaxFileSize_ProgramLang,size,suffix,name,true)
                || isSupportSize_impl(WSConfig.ListSearchSuffix_OtherFile,WSConfig.MaxFileSize_OtherFile,size,suffix,name,false);
    }
    public static PsiFile createPSIFile(String text, Language language,String fileName) {
        Project project = WSProjectListener.getInstance().getJBProject();
        PsiFile ret = PsiFileFactory.getInstance(project).createFileFromText(fileName,language,text);
        return ret;
    }
    public static PsiFile getSelectedEditorPsiFile() {
        return ReadAction.compute(()->{
            Project project = WSProjectListener.getInstance().getJBProject();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if(editor != null) {
                return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            } else {
                return null;
            }
        });
    }
    public static boolean isCurrFileTmpFile() {
        try {
            PsiFile psiFile = WSUtil.getSelectedEditorPsiFile();
            VirtualFile file = psiFile != null ? psiFile.getVirtualFile() : null;
            return isTmpFile(file);
        } catch (Exception e) {
            FSLog.log.error(e);
            return false;
        }
    }
    public static boolean isTmpFile(VirtualFile file) {
        boolean bRet = false;
        if(file != null) {
            try {
                bRet = WSProjectListener.getInstance().getWSProject().isFileTmpFile(file);
            }catch (Exception e) {
                FSLog.log.error(e);
            }
        }

        return bRet;
    }
}
