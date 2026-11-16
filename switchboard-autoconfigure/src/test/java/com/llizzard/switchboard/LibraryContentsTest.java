package com.llizzard.switchboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-008, FR-030: the library ships no consumer code. It contains no concrete strategy or resolver,
 * and no controller. Scans only the main classes ({@code target/classes}), not the test stubs.
 */
class LibraryContentsTest {

    private static final Path MAIN_CLASSES = Path.of("target", "classes").toAbsolutePath();

    /** Candidates from the library package, keeping only main classes (test stubs share the package). */
    private static List<BeanDefinition> mainClassesMatching(TypeFilter filter) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return true; // also report interfaces and abstract classes; callers filter
            }
        };
        scanner.addIncludeFilter(filter);
        return scanner.findCandidateComponents("com.llizzard.switchboard").stream()
                .filter(d -> isMainClass((AbstractBeanDefinition) d))
                .toList();
    }

    private static boolean isMainClass(AbstractBeanDefinition definition) {
        try {
            return definition.getResource() != null
                    && definition.getResource().getFile().toPath().startsWith(MAIN_CLASSES);
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<String> concreteClassNames(List<BeanDefinition> candidates) {
        return candidates.stream()
                .map(AnnotatedBeanDefinition.class::cast)
                .filter(d -> d.getMetadata().isConcrete())
                .map(BeanDefinition::getBeanClassName)
                .toList();
    }

    @Test
    void scanSeesTheLibraryClasses() {
        // Guards against a vacuous pass: the scan must find the library's own classes.
        assertThat(concreteClassNames(mainClassesMatching((reader, factory) -> true)))
                .contains(SwitchboardDispatcher.class.getName(), SwitchboardRegistry.class.getName())
                .noneMatch(name -> name.contains("Stubs") || name.endsWith("Test"));
        // ...and the type filters used below do match: they find the (non-concrete) interfaces themselves.
        assertThat(mainClassesMatching(new AssignableTypeFilter(SwitchboardStrategy.class)))
                .extracting(BeanDefinition::getBeanClassName).contains(SwitchboardStrategy.class.getName());
        assertThat(mainClassesMatching(new AssignableTypeFilter(SwitchboardResolver.class)))
                .extracting(BeanDefinition::getBeanClassName).contains(SwitchboardResolver.class.getName());
    }

    @Test
    void noConcreteStrategyInTheLibrary() {
        assertThat(concreteClassNames(mainClassesMatching(new AssignableTypeFilter(SwitchboardStrategy.class))))
                .isEmpty();
    }

    @Test
    void noConcreteResolverInTheLibrary() {
        assertThat(concreteClassNames(mainClassesMatching(new AssignableTypeFilter(SwitchboardResolver.class))))
                .isEmpty();
    }

    @Test
    void noControllerInTheLibrary() {
        assertThat(mainClassesMatching(new AnnotationTypeFilter(Controller.class))).isEmpty();
        assertThat(mainClassesMatching(new AnnotationTypeFilter(RestController.class))).isEmpty();
    }
}
