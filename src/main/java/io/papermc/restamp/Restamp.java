package io.papermc.restamp;

import io.papermc.restamp.at.AccessTransformerTypeConverter;
import io.papermc.restamp.at.ModifierTransformer;
import io.papermc.restamp.recipe.ClassATMutator;
import io.papermc.restamp.recipe.FieldATMutator;
import io.papermc.restamp.recipe.MethodATMutator;
import org.cadixdev.at.AccessTransformSet;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.Changeset;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.internal.InMemoryLargeSourceSet;

import java.util.List;

/**
 * The main executor of restamp.
 */
public class Restamp {

    /**
     * Executes restamp given the provided restamp input.
     *
     * @param input the input to restamp.
     *
     * @return the computed changeset.
     */
    @NotNull
    public static Changeset run(@NotNull final RestampInput input) {
        final ModifierTransformer modifierTransformer = new ModifierTransformer();
        final AccessTransformerTypeConverter accessTransformerTypeConverter = new AccessTransformerTypeConverter();
        final AccessTransformSet accessTransformSet = input.accessTransformers();

        final CompositeRecipe compositeRecipe = new CompositeRecipe(List.of(
            new FieldATMutator(accessTransformSet, modifierTransformer),
            new ClassATMutator(accessTransformSet, modifierTransformer),
            new MethodATMutator(accessTransformSet, modifierTransformer, accessTransformerTypeConverter)
        ));

        final InMemoryLargeSourceSet inMemoryLargeSourceSet = new InMemoryLargeSourceSet(input.sources());

        return compositeRecipe.run(inMemoryLargeSourceSet, input.executionContext()).getChangeset();
    }

}
