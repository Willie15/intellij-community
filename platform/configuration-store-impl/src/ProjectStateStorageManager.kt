/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.project.impl.ProjectImpl

class ProjectStateStorageManager(macroSubstitutor: TrackingPathMacroSubstitutor, private val project: ProjectImpl) : StateStorageManagerImpl("project", macroSubstitutor, project) {
  override fun createStorageData(fileSpec: String) = ProjectStorageData()

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
    val workspace = project.isWorkspaceComponent(component.javaClass)
    if (workspace && (operation != StateStorageOperation.READ || getOrCreateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED).hasState(componentName, false))) {
      return StoragePathMacros.WORKSPACE_FILE
    }
    return StoragePathMacros.PROJECT_FILE
  }
}