package io.papermc.restamp.function;

import io.papermc.restamp.Restamp;
import io.papermc.restamp.RestampFunctionTestHelper;
import io.papermc.restamp.RestampInput;
import org.cadixdev.at.AccessChange;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;

import java.util.List;

public class RestampFieldFunctionTest {

    @ParameterizedTest
    @ArgumentsSource(RestampFunctionTestHelper.CartesianVisibilityArgumentProvider.class)
    public void testAccessTransformerOnFieldWithOtherModifier(@NotNull final AccessChange givenVisibility,
                                                              @NotNull final AccessChange targetVisibility) {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceField("PASSPHRASE", AccessTransform.of(targetVisibility));

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(accessTransformSet, constructOuterClassFieldTestWithModifier(givenVisibility));

        final List<Result> results = Restamp.run(input).getAllResults();
        if (givenVisibility == targetVisibility) {
            Assertions.assertTrue(results.isEmpty());
            return;
        }

        Assertions.assertEquals(1, results.size());

        final SourceFile fileAfterRestamp = results.get(0).getAfter();
        Assertions.assertNotNull(fileAfterRestamp);
        Assertions.assertEquals(constructOuterClassFieldTestWithModifier(targetVisibility), fileAfterRestamp.printAll());
    }

    @ParameterizedTest
    @ArgumentsSource(RestampFunctionTestHelper.CartesianVisibilityArgumentProvider.class)
    public void testAccessTransformerOnFieldWithoutOtherModifier(@NotNull final AccessChange givenVisibility,
                                                                 @NotNull final AccessChange targetVisibility) {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceField("passphrase", AccessTransform.of(targetVisibility));

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(accessTransformSet, constructOuterClassFieldTestWithoutModifier(givenVisibility));

        final List<Result> results = Restamp.run(input).getAllResults();
        if (givenVisibility == targetVisibility) {
            Assertions.assertTrue(results.isEmpty());
            return;
        }

        Assertions.assertEquals(1, results.size());

        final SourceFile fileAfterRestamp = results.get(0).getAfter();
        Assertions.assertNotNull(fileAfterRestamp);
        Assertions.assertEquals(constructOuterClassFieldTestWithoutModifier(targetVisibility), fileAfterRestamp.printAll());
    }

    @NotNull
    private String constructOuterClassFieldTestWithModifier(@NotNull final AccessChange visibility) {
        String modifier = RestampFunctionTestHelper.accessChangeToModifierString(visibility);
        if (!modifier.isEmpty()) modifier = " " + modifier;
        return """
            package io.papermc.test;
                        
            public class Test {
                @NotNull /*inlineComment*/%s static String PASSPHRASE = "Hello World";
            }
            """.formatted(modifier);
    }

    @NotNull
    private String constructOuterClassFieldTestWithoutModifier(@NotNull final AccessChange visibility) {
        String modifier = RestampFunctionTestHelper.accessChangeToModifierString(visibility);
        if (!modifier.isEmpty()) modifier = modifier + " ";
        return """
            package io.papermc.test;
                        
            public class Test {
                /* first */ @NotNull /* second */
                /* third */ %sString passphrase = "Hello World";
            }
            """.formatted(modifier);
    }

}
