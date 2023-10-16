package io.papermc.restamp.recipe;

import io.papermc.restamp.at.AccessTransformerTypeConverter;
import io.papermc.restamp.at.ModifierWidener;
import io.papermc.restamp.utils.RecipeHelper;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.MethodDescriptor;
import org.cadixdev.bombe.type.Type;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MethodATMutator extends Recipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodATMutator.class);

    private final AccessTransformSet atDictionary;
    private final ModifierWidener modifierWidener;
    private final AccessTransformerTypeConverter atTypeConverter;

    public MethodATMutator(final AccessTransformSet atDictionary,
                           final ModifierWidener modifierWidener,
                           final AccessTransformerTypeConverter atTypeConverter) {
        this.atDictionary = atDictionary;
        this.modifierWidener = modifierWidener;
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
                final Type returnType = atTypeConverter.parse(methodDeclaration.getMethodType().getReturnType());
                final List<FieldType> parameterTypes = methodDeclaration.getMethodType().getParameterTypes().stream()
                        .map(atTypeConverter::parse)
                        .map(t -> {
                            if (!(t instanceof final FieldType fieldType)) {
                                LOGGER.warn("Method {} had unexpected non-field parameter type: {}", methodIdentifier, t);
                                return null;
                            }
                            return fieldType;
                        })
                        .toList();

                final AccessTransform method = transformerClass.getMethod(new MethodSignature(
                        methodDeclaration.getSimpleName(), new MethodDescriptor(parameterTypes, returnType)
                ));
                if (method == null) return methodDeclaration;

                return methodDeclaration.withModifiers(
                        modifierWidener.widenModifiers(method, methodDeclaration.getModifiers())
                );
            }
        };
    }
}
