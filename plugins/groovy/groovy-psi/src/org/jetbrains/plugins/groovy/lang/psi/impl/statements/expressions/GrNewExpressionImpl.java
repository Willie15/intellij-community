// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrArrayDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrInnerClassConstructorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrNewExpressionImpl extends GrCallExpressionImpl implements GrNewExpression {

  private static final ResolveCache.PolyVariantResolver<MyFakeReference> RESOLVER =
    (reference, incompleteCode) -> reference.getElement().resolveImpl(incompleteCode);

  private final MyFakeReference myFakeReference = new MyFakeReference();

  public GrNewExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "NEW expression";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitNewExpression(this);
  }

  @Override
  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    final GrArgumentList list = getArgumentList();
    if (list == null) { //so it is not anonymous class declaration
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createExpressionArgumentList();
      PsiElement last = getLastChild();
      assert last != null;
      while (last.getPrevSibling() instanceof PsiWhiteSpace || last.getPrevSibling() instanceof PsiErrorElement) {
        last = last.getPrevSibling();
        assert last != null;
      }
      ASTNode astNode = last.getNode();
      assert astNode != null;
      getNode().addChild(newList.getNode(), astNode);
    }
    return super.addNamedArgument(namedArgument);
  }

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getArgumentListGroovy();
    return super.getArgumentList();
  }

  @Override
  public GrCodeReferenceElement getReferenceElement() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getBaseClassReferenceGroovy();
    return findChildByClass(GrCodeReferenceElement.class);
  }

  @Override
  public GroovyResolveResult[] multiResolveClass() {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement != null) {
      return referenceElement.multiResolve(false);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  @Override
  public int getArrayCount() {
    final GrArrayDeclaration arrayDeclaration = getArrayDeclaration();
    if (arrayDeclaration == null) return 0;
    return arrayDeclaration.getArrayCount();
  }

  @Override
  public GrAnonymousClassDefinition getAnonymousClassDefinition() {
    return findChildByClass(GrAnonymousClassDefinition.class);
  }

  @Nullable
  @Override
  public GrArrayDeclaration getArrayDeclaration() {
    return findChildByClass(GrArrayDeclaration.class);
  }

  @Nullable
  @Override
  public GrArrayInitializer getArrayInitializer() {
    return findChildByClass(GrArrayInitializer.class);
  }

  @Nullable
  @Override
  public GrTypeArgumentList getConstructorTypeArguments() {
    return findChildByClass(GrTypeArgumentList.class);
  }

  @Override
  @NotNull
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement == null) return GroovyResolveResult.EMPTY_ARRAY;

    List<GroovyResolveResult> result = new ArrayList<>();
    for (GroovyResolveResult classResult : referenceElement.multiResolve(false)) {
      final PsiElement element = classResult.getElement();
      if (element instanceof PsiClass) {
        ContainerUtil.addAll(result, ResolveUtil.getAllClassConstructors((PsiClass)element, classResult.getSubstitutor(), null, this));
      }
    }

    return result.toArray(GroovyResolveResult.EMPTY_ARRAY);
  }

  @Override
  public GrTypeElement getTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    if (getArrayCount() > 0 || getReferenceElement() == null) {
      return GroovyResolveResult.EMPTY_ARRAY;
    }

    return TypeInferenceHelper.getCurrentContext().multiResolve(myFakeReference, incompleteCode, RESOLVER);
  }

  private GroovyResolveResult[] resolveImpl(boolean incompleteCode) {
    GrCodeReferenceElement ref = getReferenceElement();
    if (ref == null) return GroovyResolveResult.EMPTY_ARRAY;

    GroovyResolveResult classCandidate = inferClassCandidate(ref);
    if (classCandidate == null) return GroovyResolveResult.EMPTY_ARRAY;
    assert classCandidate.getElement() instanceof PsiClass;

    if (incompleteCode) {
      return PsiUtil.getConstructorCandidates(ref, classCandidate, null);
    }

    final GrArgumentList argumentList = getArgumentList();
    if (argumentList == null) return GroovyResolveResult.EMPTY_ARRAY;

    if (argumentList.getNamedArguments().length > 0 && argumentList.getExpressionArguments().length == 0) {
      PsiType mapType = GrMapType.createFromNamedArgs(argumentList, getNamedArguments());
      GroovyResolveResult[] constructorResults =
        PsiUtil.getConstructorCandidates(ref, classCandidate, new PsiType[]{mapType}); //one Map parameter, actually
      for (GroovyResolveResult result : constructorResults) {
        final PsiElement resolved = result.getElement();
        if (resolved instanceof PsiMethod) {
          PsiMethod constructor = (PsiMethod)resolved;
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();
          if (parameters.length == 1 && InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_MAP)) {
            return constructorResults;
          }
        }
      }
      final GroovyResolveResult[] emptyConstructors = PsiUtil.getConstructorCandidates(ref, classCandidate, PsiType.EMPTY_ARRAY);
      if (emptyConstructors.length > 0) {
        return emptyConstructors;
      }
    }

    PsiType[] types = PsiUtil.getArgumentTypes(ref, true);

    if (types != null) {
      types = GrInnerClassConstructorUtil.addEnclosingArgIfNeeded(types, this, (PsiClass)classCandidate.getElement());
    }
    return PsiUtil.getConstructorCandidates(ref, classCandidate, types);
  }

  @Nullable
  private static GroovyResolveResult inferClassCandidate(@NotNull GrCodeReferenceElement ref) {
    final GroovyResolveResult[] classResults = ref.multiResolve(false);
    for (GroovyResolveResult result : classResults) {
      if (result.getElement() instanceof PsiClass) {
        return result;
      }
    }
    return null;
  }

  private class MyFakeReference implements PsiPolyVariantReference {
    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return GrNewExpressionImpl.this.multiResolve(incompleteCode);
    }

    @NotNull
    @Override
    public GrNewExpressionImpl getElement() {
      return GrNewExpressionImpl.this;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
      return TextRange.EMPTY_RANGE;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return resolveMethod();
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return "new expression";
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      throw new UnsupportedOperationException("unsupported!");
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new UnsupportedOperationException("unsupported!");
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      return getManager().areElementsEquivalent(element, resolve());
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }
}
