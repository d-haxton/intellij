/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.producers.BlazeBuildFileRunConfigurationProducer.BuildTarget;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.util.containers.ContainerUtil;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Generates run/debug gutter icons for BUILD files. */
public class BuildFileRunLineMarkerContributor extends RunLineMarkerContributor {

  private static final BoolExperiment enabled = new BoolExperiment("build.run.line.markers", true);

  private static final ImmutableSet<RuleType> HANDLED_RULE_TYPES =
      ImmutableSet.of(RuleType.TEST, RuleType.BINARY);

  @SuppressWarnings("MissingOverride") // #api193: method added in 2020.1
  public boolean producesAllPossibleConfigurations(PsiFile file) {
    return false;
  }

  @Nullable
  @Override
  public Info getInfo(PsiElement element) {
    if (!enabled.getValue() || !isRunContext(element)) {
      return null;
    }
    AnAction[] actions = ExecutorAction.getActions();
    return new Info(
        AllIcons.RunConfigurations.TestState.Run,
        actions,
        element1 ->
            StringUtil.join(
                ContainerUtil.mapNotNull(actions, action -> getText(action, element1)), "\n"));
  }

  private static boolean isRunContext(PsiElement element) {
    PsiFile parentFile = element.getContainingFile();
    if (!(parentFile instanceof BuildFile)
        || ((BuildFile) parentFile).getBlazeFileType() != BlazeFileType.BuildPackage) {
      return false;
    }
    FuncallExpression rule = getRuleFuncallExpression(element);
    if (rule == null) {
      return false;
    }
    BuildTarget data = BlazeBuildFileRunConfigurationProducer.getBuildTarget(rule);
    if (data == null || data.ruleType == RuleType.LIBRARY) {
      return false;
    }
    if (HANDLED_RULE_TYPES.contains(data.ruleType)) {
      return true;
    }
    // finally, run a slower check for the underlying target type (useful for macros)
    // TODO(brendandouglas): do this asynchronously? Hard to do with RunLineMarkerProvider. Ideas:
    // - custom LineMarkerFactory delegating to LineMarkerPass
    // - dirty file status somehow?
    // - override RunLineMarkerProvider, supporting collectSlowLineMarkers
    ListenableFuture<TargetInfo> future =
        TargetFinder.findTargetInfoFuture(element.getProject(), data.label);
    try {
      TargetInfo target = future.get();
      return target != null && HANDLED_RULE_TYPES.contains(target.getRuleType());
    } catch (ProcessCanceledException | IndexNotReadyException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      // ignore
    }
    return false;
  }

  private static FuncallExpression getRuleFuncallExpression(PsiElement element) {
    PsiFile parentFile = element.getContainingFile();
    if (!(parentFile instanceof BuildFile)
        || ((BuildFile) parentFile).getBlazeFileType() != BlazeFileType.BuildPackage) {
      return null;
    }
    if (!(element instanceof LeafElement)
        || element instanceof PsiWhiteSpace
        || element instanceof PsiComment) {
      return null;
    }
    if (!(element.getParent() instanceof ReferenceExpression)) {
      return null;
    }
    PsiElement grandParent = element.getParent().getParent();
    return grandParent instanceof FuncallExpression
            && ((FuncallExpression) grandParent).isTopLevel()
        ? (FuncallExpression) grandParent
        : null;
  }
}
