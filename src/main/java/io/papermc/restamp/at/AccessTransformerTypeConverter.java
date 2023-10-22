package io.papermc.restamp.at;

import org.cadixdev.bombe.type.ArrayType;
import org.cadixdev.bombe.type.BaseType;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.ObjectType;
import org.cadixdev.bombe.type.Type;
import org.cadixdev.bombe.type.VoidType;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.java.tree.JavaType;

import java.util.function.Supplier;

/**
 * The access transformer type converter is responsible for converting between types from {@link Type} and rewrites {@link JavaType}.
 */
public class AccessTransformerTypeConverter {

    private static final FieldType OBJECT = FieldType.of(Object.class);

    /**
     * Converts the passed {@link JavaType} to a {@link Type} if possible.
     * Generic type variables are erased to a {@link java.util.Objects}, while all
     * other types are properly mapped.
     *
     * @param javaType the rewrite java type to convert into a access transformer type.
     *
     * @return the converted type.
     *
     * @throws IllegalArgumentException if the passed java type could not be converted.
     */
    @NotNull
    public Type convert(@NotNull final JavaType javaType, @NotNull final Supplier<String> debugContext) throws IllegalArgumentException {
        if (javaType instanceof final JavaType.Primitive primitive) {
            return switch (primitive) {
                case Boolean -> BaseType.BOOLEAN;
                case Byte -> BaseType.BYTE;
                case Char -> BaseType.CHAR;
                case Double -> BaseType.DOUBLE;
                case Float -> BaseType.FLOAT;
                case Int -> BaseType.INT;
                case Long -> BaseType.LONG;
                case Short -> BaseType.SHORT;
                case Void -> VoidType.INSTANCE;
                default -> throw new IllegalArgumentException("Primitive type " + primitive + " cannot be mapped! " + debugContext.get());
            };
        }

        if (javaType instanceof final JavaType.Class knownType) {
            return new ObjectType(knownType.getFullyQualifiedName());
        }

        if (javaType instanceof final JavaType.Parameterized parameterized) {
            return new ObjectType(parameterized.getFullyQualifiedName());
        }

        if (javaType instanceof JavaType.GenericTypeVariable) {
            return OBJECT;
        }

        if (javaType instanceof final JavaType.Array array) {
            int dimension = 1;
            JavaType.Array currentArrayType = array;
            while (currentArrayType.getElemType() instanceof final JavaType.Array childArrayType) {
                dimension++;
                currentArrayType = childArrayType;
            }

            final Type parsedArrayBaseType = this.convert(currentArrayType.getElemType(), debugContext);
            if (!(parsedArrayBaseType instanceof final FieldType arrayBasedFieldType))
                throw new IllegalArgumentException("Cannot convert array with non-field type base: " + array + ". " + debugContext.get());

            return new ArrayType(dimension, arrayBasedFieldType);
        }

        if (javaType instanceof final JavaType.Unknown unknown) {
            throw new IllegalArgumentException("Cannot map unexpected type: " + unknown.getClassName() + ". " + debugContext.get());
        }

        throw new IllegalArgumentException("Cannot map unexpected type: " + javaType.getJacksonPolymorphicTypeTag() + ". " + debugContext.get());
    }

}
