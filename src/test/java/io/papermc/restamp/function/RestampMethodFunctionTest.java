package io.papermc.restamp.function;

import io.papermc.restamp.Restamp;
import io.papermc.restamp.RestampFunctionTestHelper;
import io.papermc.restamp.RestampInput;
import org.cadixdev.at.AccessTransform;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.ModifierChange;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;

import java.util.List;

import static io.papermc.restamp.RestampFunctionTestHelper.accessChangeToModifierString;

@Tag("function")
public class RestampMethodFunctionTest {

    @ParameterizedTest
    @ArgumentsSource(RestampFunctionTestHelper.CartesianVisibilityArgumentProvider.class)
    public void testAccessTransformerOnMethod(@NotNull final AccessTransform given,
                                              @NotNull final AccessTransform target,
                                              @Nullable final String staticModifier) {
        final AccessTransformSet accessTransformSet = AccessTransformSet.create();
        accessTransformSet.getOrCreateClass("io.papermc.test.Test").replaceMethod(
            MethodSignature.of("test", "(Ljava.lang.Object;)Ljava.lang.String;"), target
        );

        final RestampInput input = RestampFunctionTestHelper.inputFromSourceString(accessTransformSet, constructMethodTest(
            accessChangeToModifierString(given.getAccess(), staticModifier, given.getFinal() == ModifierChange.ADD ? "final" : null)
        ));

        final List<Result> results = Restamp.run(input).getAllResults();
        if (given.equals(target)) {
            Assertions.assertTrue(results.isEmpty());
            return;
        }

        Assertions.assertEquals(1, results.size());

        final SourceFile fileAfterRestamp = results.get(0).getAfter();
        Assertions.assertNotNull(fileAfterRestamp);
        Assertions.assertEquals(constructMethodTest(
            accessChangeToModifierString(target.getAccess(), staticModifier, target.getFinal() == ModifierChange.ADD ? "final" : null)
        ), fileAfterRestamp.printAll());
    }

    @NotNull
    private String constructMethodTest(@NotNull String modifier) {
        if (!modifier.isEmpty()) modifier = modifier + " ";
        return """
            package io.papermc.test;
                        
            public class Test {
                
                /**
                * Javadocs
                */
                @NotNull
                %sString /* Comment insiede */ test(Object input) {
                    return String.valueOf(input);
                }
            }
            """.formatted(modifier);
    }

}
