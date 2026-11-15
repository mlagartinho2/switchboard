package com.llizzard.switchboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SwitchboardGroupTest {

    @Test
    void ofExposesNameAndTypes() {
        SwitchboardGroup<String, Integer> group = SwitchboardGroup.of("refunds", String.class, Integer.class);

        assertThat(group.name()).isEqualTo("refunds");
        assertThat(group.requestType()).isEqualTo(String.class);
        assertThat(group.responseType()).isEqualTo(Integer.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"refunds", "a", "refund-v2", "a1-b2-c3", "123"})
    void acceptsLowerCaseKebabNames(String name) {
        assertThat(SwitchboardGroup.of(name, String.class, String.class).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Refunds", "refunds_x", "", "-a", "a-", "a--b", "re funds", " refunds"})
    void rejectsNamesThatAreNotLowerCaseKebab(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SwitchboardGroup.of(name, String.class, String.class))
                .withMessageContaining("'" + name + "'");
    }

    @Test
    void rejectsNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> SwitchboardGroup.of(null, String.class, String.class))
                .withMessageContaining("name");
    }

    @Test
    void rejectsNullRequestType() {
        assertThatNullPointerException()
                .isThrownBy(() -> SwitchboardGroup.of("refunds", null, String.class))
                .withMessageContaining("requestType");
    }

    @Test
    void rejectsNullResponseType() {
        assertThatNullPointerException()
                .isThrownBy(() -> SwitchboardGroup.of("refunds", String.class, null))
                .withMessageContaining("responseType");
    }

    @Test
    void handlesWithSameNameAndTypesAreEqual() {
        SwitchboardGroup<String, Integer> a = SwitchboardGroup.of("refunds", String.class, Integer.class);
        SwitchboardGroup<String, Integer> b = SwitchboardGroup.of("refunds", String.class, Integer.class);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void handlesWithSameNameButDifferentRequestTypeAreNotEqual() {
        assertThat(SwitchboardGroup.of("refunds", String.class, Integer.class))
                .isNotEqualTo(SwitchboardGroup.of("refunds", Long.class, Integer.class));
    }

    @Test
    void handlesWithSameNameButDifferentResponseTypeAreNotEqual() {
        assertThat(SwitchboardGroup.of("refunds", String.class, Integer.class))
                .isNotEqualTo(SwitchboardGroup.of("refunds", String.class, Long.class));
    }

    @Test
    void handlesWithDifferentNamesAreNotEqual() {
        assertThat(SwitchboardGroup.of("refunds", String.class, Integer.class))
                .isNotEqualTo(SwitchboardGroup.of("payments", String.class, Integer.class));
    }

    @Test
    void toStringReturnsTheName() {
        assertThat(SwitchboardGroup.of("refunds", String.class, Integer.class)).hasToString("refunds");
    }
}
