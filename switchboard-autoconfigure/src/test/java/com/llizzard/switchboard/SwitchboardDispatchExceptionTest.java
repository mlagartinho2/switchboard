package com.llizzard.switchboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SwitchboardDispatchExceptionTest {

    @Test
    void unresolvedExposesGroupAndNamesItInMessage() {
        SwitchboardUnresolvedException ex = new SwitchboardUnresolvedException("refunds");

        assertThat(ex.group()).isEqualTo("refunds");
        assertThat(ex.getMessage())
                .isEqualTo("No resolver in group 'refunds' identified a use case for this request.");
    }

    @Test
    void notAvailableExposesGroupAndKeyAndNamesBothInMessage() {
        SwitchboardNotAvailableException ex = new SwitchboardNotAvailableException("refunds", "X");

        assertThat(ex.group()).isEqualTo("refunds");
        assertThat(ex.caseKey()).isEqualTo("X");
        assertThat(ex.getMessage())
                .isEqualTo("Use case 'X' in group 'refunds' is not registered or is disabled.");
    }

    @Test
    void bothAreSubtypesOfTheSealedParentAndRuntimeException() {
        assertThat(new SwitchboardUnresolvedException("refunds"))
                .isInstanceOf(SwitchboardDispatchException.class)
                .isInstanceOf(RuntimeException.class);
        assertThat(new SwitchboardNotAvailableException("refunds", "X"))
                .isInstanceOf(SwitchboardDispatchException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void parentIsSealedAndPermitsExactlyTheTwoOutcomes() {
        assertThat(SwitchboardDispatchException.class.isSealed()).isTrue();
        assertThat(SwitchboardDispatchException.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        SwitchboardUnresolvedException.class, SwitchboardNotAvailableException.class);
    }
}
