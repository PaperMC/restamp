package io.papermc.restamp.recipe;

import io.papermc.restamp.at.AccessTransformerTypeConverter;
import io.papermc.restamp.at.ModifierTransformationResult;
import io.papermc.restamp.at.ModifierTransformer;
import io.papermc.restamp.utils.RecipeHelper;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.MethodDescriptor;
import org.cadixdev.bombe.type.Type;
import org.cadixdev.bombe.type.VoidType;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The {@link MethodATMutator} recipe is responsible for applying access transformers to method definitions across the source files provided.
 */
public class MethodATMutator extends Recipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodATMutator.class);

    private final AccessTransformSet atDictionary;
    private final ModifierTransformer modifierTransformer;
    private final AccessTransformerTypeConverter atTypeConverter;

    public MethodATMutator(final AccessTransformSet atDictionary,
                           final ModifierTransformer modifierTransformer,
                           final AccessTransformerTypeConverter atTypeConverter) {
        this.atDictionary = atDictionary;
        this.modifierTransformer = modifierTransformer;
        this.atTypeConverter = atTypeConverter;
    }

    @Override
    public String getDisplayName() {
        return "Applies access transformers to methods";
    }

    @Override
    public String getDescription() {
        return "Applies pre-configured access transformers to methods in the codebase to make them more accessible";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @NotNull
            @Override
            public J.MethodDeclaration visitMethodDeclaration(@NotNull final J.MethodDeclaration unresolvedMethodDecl,
                                                              @NotNull final ExecutionContext executionContext) {
                final J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(unresolvedMethodDecl, executionContext);

                final J.ClassDeclaration parentClassDeclaration = RecipeHelper.retrieveFieldClass(getCursor());
                if (parentClassDeclaration == null || parentClassDeclaration.getType() == null)
                    return methodDeclaration;

                // Find access transformers for class
                final AccessTransformSet.Class transformerClass = atDictionary.getClass(
                    parentClassDeclaration.getType().getFullyQualifiedName()
                ).orElse(null);
                if (transformerClass == null) return methodDeclaration;

                final String methodIdentifier = parentClassDeclaration.getType().getFullyQualifiedName() + "#" + methodDeclaration.getName();

                if (methodDeclaration.getMethodType() == null) {
                    LOGGER.warn("Method {} did not have a method type!", methodIdentifier);
                    return methodDeclaration;
                }

                // Fetch access transformer to apply to specific field.
                String atMethodName = methodDeclaration.getMethodType().getName();
                Type returnType = atTypeConverter.convert(methodDeclaration.getMethodType().getReturnType(), () -> "Parsing return type " + methodDeclaration.getReturnTypeExpression().toString() + " of method " + methodIdentifier);
                final List<FieldType> parameterTypes = methodDeclaration.getMethodType().getParameterTypes().stream()
                    .map((JavaType javaType) -> atTypeConverter.convert(javaType, () -> "Parsing parameter a of method " + methodIdentifier))
                    .map(t -> {
                        if (!(t instanceof final FieldType fieldType)) {
                            LOGGER.warn("Method {} had unexpected non-field parameter type: {}", methodIdentifier, t);
                            return null;
                        }
                        return fieldType;
                    })
                    .toList();

                // Constructor are *special* in rewrite.
                if (atMethodName.equals("<constructor>")) {
                    atMethodName = "<init>";
                    returnType = VoidType.INSTANCE;
                }

                final AccessTransform accessTransform = transformerClass.getMethod(new MethodSignature(
                    atMethodName, new MethodDescriptor(parameterTypes, returnType)
                ));
                if (accessTransform == null || accessTransform.isEmpty()) return methodDeclaration;

                final TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
                final ModifierTransformationResult transformationResult = modifierTransformer.transformModifiers(
                    accessTransform,
                    methodDeclaration.getModifiers(),
                    Optional.ofNullable(returnTypeExpression).map(J::getPrefix).orElse(methodDeclaration.getName().getPrefix())
                );

                if (transformationResult.newModifiers().equals(methodDeclaration.getModifiers())) {
                    return methodDeclaration;
                }
                transformerClass.replaceMethod(new MethodSignature(
                    atMethodName, new MethodDescriptor(parameterTypes, returnType)
                ), AccessTransform.EMPTY); // Mark as consumed

                J.MethodDeclaration updated = methodDeclaration.withModifiers(transformationResult.newModifiers());
                if (returnTypeExpression != null) {
                    updated = updated.withReturnTypeExpression(returnTypeExpression.withPrefix(transformationResult.parentSpace()));
                } else {
                    updated = updated.withName(updated.getName().withPrefix(transformationResult.parentSpace()));
                }

                return updated;
            }
        };
    }

}
