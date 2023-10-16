package io.papermc.restamp.at;

import org.cadixdev.bombe.type.ArrayType;
import org.cadixdev.bombe.type.BaseType;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.ObjectType;
import org.cadixdev.bombe.type.Type;
import org.cadixdev.bombe.type.VoidType;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.java.tree.JavaType;

public class AccessTransformerTypeConverter {

    private static final FieldType OBJECT = FieldType.of(Object.class);

    @NotNull
    public Type parse(@NotNull final JavaType javaType) throws IllegalArgumentException {
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
                default -> throw new IllegalArgumentException("Primitive type " + primitive + " cannot be mapped!");
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

            final Type parsedArrayBaseType = this.parse(currentArrayType.getElemType());
            if (!(parsedArrayBaseType instanceof final FieldType arrayBasedFieldType))
                throw new IllegalArgumentException("Cannot convert array with non-field type base: " + array);

            return new ArrayType(dimension, arrayBasedFieldType);
        }

        throw new IllegalArgumentException("Cannot map unexpected type " + javaType.getJacksonPolymorphicTypeTag());
    }

}
