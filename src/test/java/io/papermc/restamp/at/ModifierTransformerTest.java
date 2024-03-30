package io.papermc.restamp.at;

import io.papermc.restamp.RestampFunctionTestHelper;
import io.papermc.restamp.utils.RecipeHelper;
import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.ModifierChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.papermc.restamp.RestampFunctionTestHelper.modifierFrom;

class ModifierTransformerTest {

    private final ModifierTransformer transformer = new ModifierTransformer();

    static Stream<Arguments> testSkipModifyTransformation() {
        return Stream.of(
            Arguments.of(
                AccessTransform.of(AccessChange.PUBLIC, ModifierChange.REMOVE),
                List.of(modifierFrom(Space.EMPTY, J.Modifier.Type.Public)),
                false
            ),
            Arguments.of(
                AccessTransform.of(AccessChange.PACKAGE_PRIVATE, ModifierChange.ADD),
                List.of(modifierFrom(Space.EMPTY, J.Modifier.Type.Final)),
                false
            ),
            Arguments.of(
                AccessTransform.of(AccessChange.PUBLIC),
                List.of(modifierFrom(Space.EMPTY, J.Modifier.Type.Private)),
                true
            ),
            Arguments.of(
                AccessTransform.of(AccessChange.PUBLIC),
                List.of(modifierFrom(Space.EMPTY, J.Modifier.Type.Private), modifierFrom(Space.EMPTY, J.Modifier.Type.Final)),
                true
            ),
            Arguments.of(
                AccessTransform.of(ModifierChange.REMOVE),
                List.of(modifierFrom(Space.EMPTY, J.Modifier.Type.Public), modifierFrom(Space.EMPTY, J.Modifier.Type.Final)),
                true
            )
        );
    }

    @MethodSource()
    @ParameterizedTest
    public void testSkipModifyTransformation(@NotNull final AccessTransform transform,
                                             @NotNull final List<J.Modifier> modifiers,
                                             final boolean expected) {
        final ModifierTransformationResult result = this.transformer.transformModifiers(transform, modifiers, Space.EMPTY);
        Assertions.assertEquals(expected, !result.newModifiers().equals(modifiers));
    }

    @ParameterizedTest
    @ArgumentsSource(RestampFunctionTestHelper.CartesianVisibilityArgumentProvider.class)
    public void testModifyVisibilityTransformation(@NotNull final AccessTransform currentState,
                                                   @NotNull final AccessTransform targetState,
                                                   @NotNull final AccessTransform instruction,
                                                   @Nullable final String staticModifierExisting) {
        final List<J.Modifier> modifiers = new ArrayList<>();
        final J.Modifier.Type type = switch (currentState.getAccess()) {
            case PRIVATE -> J.Modifier.Type.Private;
            case PROTECTED -> J.Modifier.Type.Protected;
            case PUBLIC -> J.Modifier.Type.Public;
            default -> null;
        };
        if (type != null) {
            modifiers.add(modifierFrom(Space.EMPTY, type));
        }
        if (staticModifierExisting != null) modifiers.add(modifierFrom(Space.SINGLE_SPACE, J.Modifier.Type.Static));
        if (currentState.getFinal() == ModifierChange.ADD) modifiers.add(modifierFrom(Space.SINGLE_SPACE, J.Modifier.Type.Final));

        final ModifierTransformationResult result = transformer.transformModifiers(instruction, modifiers, Space.SINGLE_SPACE);

        final List<J.Modifier> newModifiers = result.newModifiers();

        int expectedModifierSize = 0;
        if (targetState.getAccess() != AccessChange.PACKAGE_PRIVATE) expectedModifierSize++;
        if (targetState.getFinal() == ModifierChange.ADD) expectedModifierSize++;
        if (staticModifierExisting != null) expectedModifierSize++;

        Assertions.assertEquals(expectedModifierSize, newModifiers.size());
        if (targetState.getAccess() != AccessChange.PACKAGE_PRIVATE) {
            Assertions.assertEquals(RecipeHelper.typeFromAccessChange(targetState.getAccess()), newModifiers.remove(0).getType());
        }
        if (staticModifierExisting != null) Assertions.assertEquals(J.Modifier.Type.Static, newModifiers.remove(0).getType());
        if (targetState.getFinal() == ModifierChange.ADD) Assertions.assertEquals(J.Modifier.Type.Final, newModifiers.remove(0).getType());
    }

    @Test
    public void testSpaceShiftOntoInsertedVisibility() {
        final TextComment comment = new TextComment(false, "hii", "", Markers.EMPTY);

        final List<J.Modifier> modifiers = List.of(
            modifierFrom(Space.build("  ", List.of(comment)), J.Modifier.Type.Final),
            modifierFrom(Space.SINGLE_SPACE.withComments(List.of(comment)), J.Modifier.Type.Private),
            modifierFrom(Space.SINGLE_SPACE, J.Modifier.Type.Static)
        );

        final ModifierTransformationResult result = transformer.transformModifiers(
            AccessTransform.of(AccessChange.PUBLIC, ModifierChange.REMOVE), modifiers, Space.SINGLE_SPACE
        );

        Assertions.assertEquals(Space.SINGLE_SPACE, result.parentSpace());
        Assertions.assertEquals(2, result.newModifiers().size());

        final J.Modifier insertedPublicModifier = result.newModifiers().get(0);
        Assertions.assertEquals(J.Modifier.Type.Public, insertedPublicModifier.getType());
        Assertions.assertEquals("  ", insertedPublicModifier.getPrefix().getWhitespace());
        Assertions.assertEquals(2, insertedPublicModifier.getComments().size());
        Assertions.assertEquals(comment, insertedPublicModifier.getComments().get(0));
        Assertions.assertEquals(comment, insertedPublicModifier.getComments().get(1));
    }

}
