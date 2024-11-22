package io.papermc.restamp.utils;

import org.cadixdev.at.AccessChange;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;

/**
 * The recipe helper type is a pure utility class that holds static helper methods for working with {@link org.openrewrite.Recipe} types.
 */
@NullMarked
public class RecipeHelper {

    /**
     * Retrieves the first class declaration found in the cursor by traversing its parents
     * that is directly above the cursor.
     * <p>
     * If the cursor is in a method declaration, null is returned as the field definition is a local in-method declaration.
     *
     * @param cursor the cursor to traverse from.
     *
     * @return the class declaration or null if no matching one was found.
     */
    public static J.@Nullable ClassDeclaration retrieveFieldClass(final Cursor cursor) {
        final Object foundParent = cursor.dropParentUntil(parent ->
            parent instanceof J.ClassDeclaration || parent instanceof J.MethodDeclaration || parent instanceof J.Lambda
        ).getValue();
        if (foundParent instanceof final J.ClassDeclaration parentClass) return parentClass;
        return null;
    }

    /**
     * Converts the access change into a concrete modifier type if possible.
     * If {@link AccessChange#PACKAGE_PRIVATE} is returned, {@code null} is yielded by this method, indicating no modifier is needed.
     *
     * @param accessChange the access change to convert into a modifier type.
     *
     * @return the modifier type.
     */
    public static J.Modifier.@Nullable Type typeFromAccessChange(final AccessChange accessChange) {
        return switch (accessChange) {
            case PRIVATE -> J.Modifier.Type.Private;
            case PUBLIC -> J.Modifier.Type.Public;
            case PROTECTED -> J.Modifier.Type.Protected;
            default -> null;
        };
    }

}
