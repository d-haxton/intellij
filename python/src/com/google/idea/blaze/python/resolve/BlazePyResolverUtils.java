/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.resolve;

import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.TrackedOutputArtifacts;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import java.io.File;
import java.util.Optional;
import javax.annotation.Nullable;

/** Utility methods for {@link com.jetbrains.python.psi.impl.PyImportResolver}s */
public class BlazePyResolverUtils {

  /**
   * Looks for a PsiDirectory or PyFile at the given 'blaze-genfiles'-relative path (appending '.py'
   * to the path when looking for py files).
   */
  @Nullable
  public static PsiElement resolveGenfilesPath(
      PyQualifiedNameResolveContext context, String relativePath) {
    return resolveGenfilesPath(context.getProject(), relativePath)
        .map(f -> resolveFile(context.getPsiManager(), f))
        .orElse(null);
  }

  /** Resolves a genfiles-relative path to a locally-accessible file. */
  private static Optional<File> resolveGenfilesPath(Project project, String relativePath) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return Optional.empty();
    }
    TrackedOutputArtifacts artifacts = TrackedOutputArtifacts.fromProjectData(projectData);
    OutputArtifact artifact = artifacts.resolveGenfilesPath(relativePath);
    if (artifact == null) {
      artifact = artifacts.resolveGenfilesPath(relativePath + ".py");
    }
    if (artifact != null) {
      return Optional.ofNullable(OutputArtifactResolver.resolve(project, artifact));
    }
    return Optional.of(new File(projectData.getBlazeInfo().getGenfilesDirectory(), relativePath));
  }

  /**
   * Looks for a PsiDirectory or PyFile at the given workspace-relative path (appending '.py' to the
   * path when looking for py files).
   */
  @Nullable
  public static PsiElement resolvePath(PyQualifiedNameResolveContext context, String relativePath) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(context.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    WorkspacePathResolver pathResolver = projectData.getWorkspacePathResolver();
    File file = pathResolver.resolveToFile(relativePath);
    return resolveFile(context.getPsiManager(), file);
  }

  @Nullable
  public static PsiFileSystemItem resolveFile(PsiManager manager, File file) {
    VirtualFile vf =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByPath(file.getPath());
    if (vf != null) {
      return vf.isDirectory() ? manager.findDirectory(vf) : manager.findFile(vf);
    }
    vf = VirtualFileSystemProvider.getInstance().getSystem().findFileByPath(file.getPath() + ".py");
    return vf != null ? manager.findFile(vf) : null;
  }
}
