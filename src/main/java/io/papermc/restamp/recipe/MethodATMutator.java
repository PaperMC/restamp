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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.TypeTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The {@link MethodATMutator} recipe is responsible for applying access transformers to method definitions across the source files provided.
 */
@NullMarked
public class MethodATMutator extends Recipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodATMutator.class);

    private final AccessTransformSet atDictionary;
    private final AccessTransformSet inheritanceAccessTransformAtDirectory;
    private final ModifierTransformer modifierTransformer;
    private final AccessTransformerTypeConverter atTypeConverter;

    public MethodATMutator(final AccessTransformSet atDictionary,
                           final ModifierTransformer modifierTransformer,
                           final AccessTransformerTypeConverter atTypeConverter) {
        this.atDictionary = atDictionary;
        this.modifierTransformer = modifierTransformer;
        this.atTypeConverter = atTypeConverter;

        // Create a copy of the atDirectory for inherited at lookups.
        // Needed as the parent type may be processed first, removing its access transformer for tracking purposes.
        // Child types hence lookup using this.
        this.inheritanceAccessTransformAtDirectory = AccessTransformSet.create();
        this.inheritanceAccessTransformAtDirectory.merge(this.atDictionary);
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(final J.MethodDeclaration unresolvedMethodDecl,
                                                              final ExecutionContext executionContext) {
                final J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(unresolvedMethodDecl, executionContext);

                final J.ClassDeclaration parentClassDeclaration = RecipeHelper.retrieveFieldClass(getCursor());
                if (parentClassDeclaration == null || parentClassDeclaration.getType() == null)
                    return methodDeclaration;

                final String methodIdentifier = parentClassDeclaration.getType().getFullyQualifiedName() + "#" + methodDeclaration.getName();

                if (methodDeclaration.getMethodType() == null) {
                    LOGGER.warn("Method {} did not have a method type!", methodIdentifier);
                    return methodDeclaration;
                }

                // Fetch access transformer to apply to specific method.
                String atMethodName = methodDeclaration.getMethodType().getName();
                Type returnType = atTypeConverter.convert(methodDeclaration.getMethodType().getReturnType(),
                    () -> "Parsing return type " + methodDeclaration.getReturnTypeExpression().toString() + " of method " + methodIdentifier);
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

                // Find access transformers for method
                final AccessTransform accessTransform = findApplicableAccessTransformer(
                    parentClassDeclaration.getType(),
                    atMethodName,
                    returnType,
                    parameterTypes
                );
                if (accessTransform == null) return methodDeclaration;

                final TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
                final ModifierTransformationResult transformationResult = modifierTransformer.transformModifiers(
                    accessTransform,
                    methodDeclaration.getModifiers(),
                    Optional.ofNullable(returnTypeExpression).map(J::getPrefix).orElse(methodDeclaration.getName().getPrefix())
                );

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

    /**
     * Finds the applicable access transformer for a method and *optionally* removes it from the atDirectory.
     *
     * @param owningType     the owning type of the method, e.g. the type it is defined in.
     * @param atMethodName   the method name.
     * @param returnType     the return type.
     * @param parameterTypes the method parameters.
     *
     * @return the access transformer or null.
     */
    @Nullable
    private AccessTransform findApplicableAccessTransformer(
        final FullyQualified owningType,
        final String atMethodName,
        final Type returnType,
        final List<FieldType> parameterTypes
    ) {
        final MethodSignature methodSignature = new MethodSignature(
            atMethodName,
            new MethodDescriptor(parameterTypes, returnType)
        );

        for (FullyQualified currentCheckedType = owningType; currentCheckedType != null; currentCheckedType = currentCheckedType.getSupertype()) {
            // The class at data from the copy of the at dir.
            // Removal of these happens later but we need the original state to ensure overrides are updated.
            final AccessTransformSet.Class transformerClass = inheritanceAccessTransformAtDirectory
                .getClass(currentCheckedType.getFullyQualifiedName())
                .orElse(null);
            if (transformerClass == null) continue;

            // Only get the method here.
            final AccessTransform accessTransform = transformerClass.getMethod(methodSignature);
            if (accessTransform == null || accessTransform.isEmpty()) continue;

            // If we *did* find an AT here and this *is* the direct owning type, remove it from the original atDirectory.
            if (currentCheckedType == owningType) {
                atDictionary.getClass(transformerClass.getName()).ifPresent(c -> c.replaceMethod(methodSignature, AccessTransform.EMPTY));
            }
            return accessTransform;
        }

        return null; // We did not find anything applicable.
    }

}
