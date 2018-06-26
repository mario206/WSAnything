// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package WSAnything.Core;

import com.intellij.find.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.search.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WSFindInProjectUtil {
  private static final int USAGES_PER_READ_ACTION = 100;

  private WSFindInProjectUtil() {}

  private static void addSourceDirectoriesFromLibraries(@NotNull Project project,
                                                        @NotNull VirtualFile directory,
                                                        @NotNull Collection<VirtualFile> outSourceRoots) {
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    // if we already are in the sources, search just in this directory only
    if (!index.isInLibraryClasses(directory)) return;
    VirtualFile classRoot = index.getClassRootForFile(directory);
    if (classRoot == null) return;
    String relativePath = VfsUtilCore.getRelativePath(directory, classRoot);
    if (relativePath == null) return;

    Collection<VirtualFile> otherSourceRoots = new THashSet<>();

    // if we are in the library sources, return (to search in this directory only)
    // otherwise, if we outside sources or in a jar directory, add directories from other source roots
    searchForOtherSourceDirs:
    for (OrderEntry entry : index.getOrderEntriesForFile(directory)) {
      if (entry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library == null) continue;
        // note: getUrls() returns jar directories too
        String[] sourceUrls = library.getUrls(OrderRootType.SOURCES);
        for (String sourceUrl : sourceUrls) {
          if (VfsUtilCore.isEqualOrAncestor(sourceUrl, directory.getUrl())) {
            // already in this library sources, no need to look for another source root
            otherSourceRoots.clear();
            break searchForOtherSourceDirs;
          }
          // otherwise we may be inside the jar file in a library which is configured as a jar directory
          // in which case we have no way to know whether this is a source jar or classes jar - so try to locate the source jar
        }
      }
      for (VirtualFile sourceRoot : entry.getFiles(OrderRootType.SOURCES)) {
        VirtualFile sourceFile = sourceRoot.findFileByRelativePath(relativePath);
        if (sourceFile != null) {
          otherSourceRoots.add(sourceFile);
        }
      }
    }
    outSourceRoots.addAll(otherSourceRoots);
  }

  @NotNull
  static public SearchScope getScopeFromModel(@NotNull Project project, @NotNull FindModel findModel) {
    SearchScope customScope = findModel.getCustomScope();
    VirtualFile directory = getDirectory(findModel);
    Module module = findModel.getModuleName() == null ? null : ModuleManager.getInstance(project).findModuleByName(findModel.getModuleName());
    return findModel.isCustomScope() && customScope != null ? customScope.intersectWith(GlobalSearchScope.allScope(project)) :
           // we don't have to check for myProjectFileIndex.isExcluded(file) here like FindInProjectTask.collectFilesInScope() does
           // because all found usages are guaranteed to be not in excluded dir
           directory != null ? forDirectory(project, findModel.isWithSubdirectories(), directory) :
           module != null ? module.getModuleContentScope() :
           findModel.isProjectScope() ? ProjectScope.getContentScope(project) :
           GlobalSearchScope.allScope(project);
  }

  @NotNull
  private static GlobalSearchScope forDirectory(@NotNull Project project,
                                                boolean withSubdirectories,
                                                @NotNull VirtualFile directory) {
    Set<VirtualFile> result = new LinkedHashSet<>();
    result.add(directory);
    addSourceDirectoriesFromLibraries(project, directory, result);
    VirtualFile[] array = result.toArray(VirtualFile.EMPTY_ARRAY);
    return GlobalSearchScopesCore.directoriesScope(project, withSubdirectories, array);
  }

  @Nullable
  public static VirtualFile getDirectory(@NotNull FindModel findModel) {
    String directoryName = findModel.getDirectoryName();
    if (findModel.isProjectScope() || StringUtil.isEmptyOrSpaces(directoryName)) {
      return null;
    }

    String path = FileUtil.toSystemIndependentName(directoryName);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null || !virtualFile.isDirectory()) {
      virtualFile = null;
      @SuppressWarnings("deprecation") VirtualFileSystem[] fileSystems = ApplicationManager.getApplication().getComponents(VirtualFileSystem.class);
      for (VirtualFileSystem fs : fileSystems) {
        if (fs instanceof LocalFileProvider) {
          @SuppressWarnings("deprecation") VirtualFile file = ((LocalFileProvider)fs).findLocalVirtualFileByPath(path);
          if (file != null && file.isDirectory()) {
            if (file.getChildren().length > 0) {
              virtualFile = file;
              break;
            }
            if (virtualFile == null) {
              virtualFile = file;
            }
          }
        }
      }
    }
    return virtualFile;
  }
}
