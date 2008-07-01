/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.facet.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetModel;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class FacetDetector<T, C extends FacetConfiguration> {
  private final String myId;

  protected FacetDetector(final @NotNull @NonNls String id) {
    myId = id;
  }

  /**
   * @deprecated use {@link FacetDetector#FacetDetector(String)} instead
   */
  protected FacetDetector() {
    myId = getClass().getName();
  }

  public final String getId() {
    return myId;
  }

  @Nullable
  public abstract C detectFacet(T source, Collection<C> existentFacetConfigurations);

  public void beforeFacetAdded(@NotNull Facet facet, final FacetModel facetModel, @NotNull ModifiableRootModel modifiableRootModel) {
  }

  public void afterFacetAdded(@NotNull Facet facet) {
  }

}
