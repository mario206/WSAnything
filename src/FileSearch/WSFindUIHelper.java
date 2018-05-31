package FileSearch;

/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.intellij.find.FindModel;
import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@SuppressWarnings("WeakerAccess")
public class WSFindUIHelper {
    @NotNull private final Project myProject;
    private FindModel model;
    FindModel myPreviousModel;

    WSFindUIHelper(@NotNull Project project) {
        myProject = project;
        model = new FindModel();
    }


    boolean canSearchThisString() {
        return true;
    }


    public Project getProject() {
        return myProject;
    }

    public FindModel getModel() {
        return model;
    }

    public void setModel(@NotNull FindModel model) {
    }

    public void setOkHandler(@NotNull Runnable okHandler) {
    }

    public void showUI() {
    }

    public void dispose() {

    }

    void updateFindSettings() {
    }

    boolean isUseSeparateView() {
        return true;
    }

    boolean isSkipResultsWithOneUsage() {
        return true;
    }

    void setUseSeparateView(boolean separateView) {
    }

    void setSkipResultsWithOneUsage(boolean skip) {

    }

    String getTitle() {
        return "WSAnything";
    }

    public boolean isReplaceState() {
        return false;
    }

    public Runnable getOkHandler() {
        return null;
    }

    public void doOKAction() {

    }


}
