package io.papermc.restamp.utils;

import org.jetbrains.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;

public class RecipeHelper {

    @Nullable
    public static J.ClassDeclaration retrieveFieldClass(final Cursor cursor) {
        final Object foundParent = cursor.dropParentUntil(parent ->
                parent instanceof J.ClassDeclaration || parent instanceof J.MethodDeclaration
        ).getValue();
        if (foundParent instanceof final J.ClassDeclaration parentClass) return parentClass;
        return null;
    }
}
