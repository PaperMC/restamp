package io.papermc.restamp.function;

import io.papermc.restamp.Restamp;
import io.papermc.restamp.RestampFunctionTestHelper;
import io.papermc.restamp.RestampInput;
import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;

import java.util.List;

public class RestampMethodFunctionTest {

    @ParameterizedTest
    @ArgumentsSource(RestampFunctionTestHelper.CartesianVisibilityArgumentProvider.class)
    public void testAccessTransformerOnMethodWithOtherModifier(@NotNull final AccessChange givenVisibility,
                                                               @NotNull final AccessChange targetVisibility) {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceMethod(
            MethodSignature.of("test", "(Ljava.lang.Object;)Ljava.lang.String;"), AccessTransform.of(targetVisibility)
        );

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(accessTransformSet, constructWithModifier(givenVisibility));

        final List<Result> results = Restamp.run(input).getAllResults();
        if (givenVisibility == targetVisibility) {
            Assertions.assertTrue(results.isEmpty());
            return;
        }

        Assertions.assertEquals(1, results.size());

        final SourceFile fileAfterRestamp = results.get(0).getAfter();
        Assertions.assertNotNull(fileAfterRestamp);
        Assertions.assertEquals(constructWithModifier(targetVisibility), fileAfterRestamp.printAll());
    }

    @ParameterizedTest
    @ArgumentsSource(RestampFunctionTestHelper.CartesianVisibilityArgumentProvider.class)
    public void testAccessTransformerOnMethodWithoutOtherModifier(@NotNull final AccessChange givenVisibility,
                                                                  @NotNull final AccessChange targetVisibility) {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceMethod(
            MethodSignature.of("test", "(Ljava.lang.Object;)Ljava.lang.String;"), AccessTransform.of(targetVisibility)
        );

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(accessTransformSet,
                constructWithoutModifier(givenVisibility));

        final List<Result> results = Restamp.run(input).getAllResults();
        if (givenVisibility == targetVisibility) {
            Assertions.assertTrue(results.isEmpty());
            return;
        }

        Assertions.assertEquals(1, results.size());

        final SourceFile fileAfterRestamp = results.get(0).getAfter();
        Assertions.assertNotNull(fileAfterRestamp);
        Assertions.assertEquals(constructWithoutModifier(targetVisibility), fileAfterRestamp.printAll());
    }

    @NotNull
    private String constructWithModifier(@NotNull final AccessChange visibility) {
        String modifier = RestampFunctionTestHelper.accessChangeToModifierString(visibility);
        if (!modifier.isEmpty()) modifier = modifier + " ";
        return """
            package io.papermc.test;
                        
            public class Test {
                @NotNull
                %sstatic @NotNull String test(@NotNull final Object obj) {
                  return String.valueOf(obj);
                }
            }
            """.formatted(modifier);
    }

    @NotNull
    private String constructWithoutModifier(@NotNull final AccessChange visibility) {
        String modifier = RestampFunctionTestHelper.accessChangeToModifierString(visibility);
        if (!modifier.isEmpty()) modifier = modifier + " ";
        return """
            package io.papermc.test;
                        
            public class Test {
                @NotNull
                @NotNull %sString test(@NotNull final Object obj) {
                  return String.valueOf(obj);
                }
            }
            """.formatted(modifier);
    }

}
