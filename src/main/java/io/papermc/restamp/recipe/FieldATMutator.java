package io.papermc.restamp.recipe;

import io.papermc.restamp.at.ModifierTransformationResult;
import io.papermc.restamp.at.ModifierTransformer;
import io.papermc.restamp.utils.RecipeHelper;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.Objects;
import java.util.Optional;

/**
 * The {@link FieldATMutator} recipe is responsible for applying access transformers to field definitions across the source files provided.
 */
public class FieldATMutator extends Recipe {

    private final AccessTransformSet atDictionary;
    private final ModifierTransformer modifierTransformer;

    public FieldATMutator(final AccessTransformSet atDictionary, final ModifierTransformer modifierTransformer) {
        this.atDictionary = atDictionary;
        this.modifierTransformer = modifierTransformer;
    }

    @Override
    public String getDisplayName() {
        return "Applies access transformers to fields";
    }

    @Override
    public String getDescription() {
        return "Applies pre-configured access transformers to fields in the codebase to make them more accessible";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            @NotNull
            public J.VariableDeclarations visitVariableDeclarations(@NotNull final J.VariableDeclarations multiVariable,
                                                                    @NotNull final ExecutionContext executionContext) {
                final J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, executionContext);

                final J.ClassDeclaration parentClassDeclaration = RecipeHelper.retrieveFieldClass(getCursor());
                if (parentClassDeclaration == null || parentClassDeclaration.getType() == null)
                    return variableDeclarations;

                // Find access transformers for class
                final AccessTransformSet.Class transformerClass = atDictionary.getClass(
                    parentClassDeclaration.getType().getFullyQualifiedName()
                ).orElse(null);
                if (transformerClass == null) return variableDeclarations;

                // Fetch access transformer to apply to specific field.
                final AccessTransform accessTransformToApply = variableDeclarations.getVariables().stream()
                    .map(n -> transformerClass.getField(n.getSimpleName()))
                    .filter(Objects::nonNull)
                    .reduce(AccessTransform::merge)
                    .orElse(AccessTransform.EMPTY);
                if (accessTransformToApply.isEmpty()) return variableDeclarations;

                // Compute and set new módifiers
                final ModifierTransformationResult transformationResult = modifierTransformer.transformModifiers(
                    accessTransformToApply,
                    variableDeclarations.getModifiers(),
                    Optional.ofNullable(variableDeclarations.getTypeExpression()).map(J::getPrefix).orElse(Space.EMPTY)
                );

                if (transformationResult.newModifiers().equals(variableDeclarations.getModifiers())) {
                    return variableDeclarations;
                }
                variableDeclarations.getVariables().forEach(n -> transformerClass.replaceField(n.getSimpleName(), AccessTransform.EMPTY)); // Mark as consumed

                final J.VariableDeclarations updated = variableDeclarations
                    .withModifiers(transformationResult.newModifiers())
                    .withTypeExpression(variableDeclarations.getTypeExpression().withPrefix(transformationResult.parentSpace()));
                return updated;
            }
        };
    }

}
