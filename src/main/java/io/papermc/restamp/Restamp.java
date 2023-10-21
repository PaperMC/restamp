package io.papermc.restamp;

import io.papermc.restamp.at.AccessTransformerTypeConverter;
import io.papermc.restamp.at.ModifierTransformer;
import io.papermc.restamp.recipe.ClassATMutator;
import io.papermc.restamp.recipe.FieldATMutator;
import io.papermc.restamp.recipe.MethodATMutator;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.Changeset;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.internal.InMemoryLargeSourceSet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
            new MethodATMutator(accessTransformSet, modifierTransformer, accessTransformerTypeConverter),
            new ClassATMutator(accessTransformSet, modifierTransformer)
        ));

        final InMemoryLargeSourceSet inMemoryLargeSourceSet = new InMemoryLargeSourceSet(input.sources());

        final Changeset changeset = compositeRecipe.run(inMemoryLargeSourceSet, input.executionContext()).getChangeset();

        // Delete all classes that have no access transformers left to apply.
        final List<AccessTransformSet.Class> atClassSet = new ArrayList<>(accessTransformSet.getClasses().values());
        atClassSet.removeIf(c ->
            c.get().isEmpty()
                && c.getFields().values().stream().allMatch(AccessTransform::isEmpty)
                && c.getMethods().values().stream().allMatch(AccessTransform::isEmpty)
        );
        if (atClassSet.isEmpty() || !input.failWithNotApplicableAccessTransformers()) return changeset;

        // Not all ats applied, error if configured to do so.
        final String notAppliedAccessTransformers = atClassSet.stream().map(c ->
            "%s: [%s] {%s}".formatted(
                c.getName(),
                String.join(", ", c.getFields().keySet()),
                c.getMethods().keySet().stream().map(MethodSignature::toJvmsIdentifier).collect(Collectors.joining(", "))
            )
        ).collect(Collectors.joining(",\n"));

        throw new IllegalStateException("Could not apply access transformers: " + notAppliedAccessTransformers);
    }

}
