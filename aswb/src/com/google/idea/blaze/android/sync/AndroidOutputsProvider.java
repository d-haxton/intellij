/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.OutputsProvider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Used to track blaze artifacts relevant to android projects. */
public class AndroidOutputsProvider implements OutputsProvider {
  @Override
  public boolean isActive(WorkspaceLanguageSettings languageSettings) {
    return languageSettings.isLanguageActive(LanguageClass.ANDROID);
  }

  @Override
  public Collection<ArtifactLocation> selectOutputsToCache(TargetIdeInfo target) {
    // other outputs are handled separately to RemoteOutputsCache
    if (target.getJavaToolchainIdeInfo() != null) {
      return target.getJavaToolchainIdeInfo().getJavacJars();
    }
    if (target.getAndroidIdeInfo() != null) {
      return getAndroidSources(target.getAndroidIdeInfo());
    }
    return ImmutableList.of();
  }

  private static Collection<ArtifactLocation> getAndroidSources(AndroidIdeInfo androidInfo) {
    Set<ArtifactLocation> fileSet = new HashSet<>();

    ArtifactLocation manifest = androidInfo.getManifest();
    if (manifest != null) {
      fileSet.add(manifest);
    }
    fileSet.addAll(androidInfo.getResources());
    for (AndroidResFolder androidResFolder : androidInfo.getResFolders()) {
      fileSet.add(androidResFolder.getRoot());
    }
    return fileSet;
  }
}
