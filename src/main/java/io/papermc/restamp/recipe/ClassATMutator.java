package io.papermc.restamp.recipe;

import io.papermc.restamp.at.ModifierWidener;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class ClassATMutator extends Recipe {

    private final AccessTransformSet atDictionary;
    private final ModifierWidener modifierWidener;

    public ClassATMutator(final AccessTransformSet atDictionary, final ModifierWidener modifierWidener) {
        this.atDictionary = atDictionary;
        this.modifierWidener = modifierWidener;
    }

    @Override
    public String getDisplayName() {
        return "Applies access transformers to classes";
    }

    @Override
    public String getDescription() {
        return "Applies pre-configured access transformers to classes in the codebase to make them more accessible";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @NotNull
            @Override
            public J.ClassDeclaration visitClassDeclaration(@NotNull final J.ClassDeclaration unresolvedClassDeclaration,
                                                            @NotNull final ExecutionContext executionContext) {
                final J.ClassDeclaration classDeclaration = super.visitClassDeclaration(unresolvedClassDeclaration, executionContext);
                if (classDeclaration.getType() == null) return classDeclaration;

                // Find access transformers for class
                final AccessTransformSet.Class transformerClass = atDictionary.getClass(
                        classDeclaration.getType().getFullyQualifiedName()
                ).orElse(null);
                if (transformerClass == null) return classDeclaration;

                final AccessTransform accessTransform = transformerClass.get();
                return classDeclaration.withModifiers(
                        modifierWidener.widenModifiers(accessTransform, classDeclaration.getModifiers())
                );
            }
        };
    }
}
