package com.llizzard.switchboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-008, FR-030: the library ships no consumer code. It contains no concrete strategy or resolver,
 * and no controller. Scans only the main classes ({@code target/classes}), not the test stubs.
 */
class LibraryContentsTest {

    private static final String MAIN_CLASSES = Path.of("target", "classes").toUri().toString();

    private static Set<BeanDefinition> mainClassesMatching(org.springframework.core.type.filter.TypeFilter filter) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourcePattern("**/*.class");
        scanner.setResourceLoader(new PathMatchingResourcePatternResolver(new FileSystemResourceLoader()));
        scanner.addIncludeFilter(filter);
        return scanner.findCandidateComponents(MAIN_CLASSES + "com/llizzard/switchboard");
    }

    private static List<String> concreteClassNames(Set<BeanDefinition> candidates) {
        return candidates.stream()
                .map(AnnotatedBeanDefinition.class::cast)
                .filter(d -> d.getMetadata().isConcrete())
                .map(BeanDefinition::getBeanClassName)
                .toList();
    }

    @Test
    void scanSeesTheLibraryClasses() {
        // Guards against a vacuous pass: the scan must find the library's own classes.
        assertThat(concreteClassNames(mainClassesMatching(new AssignableTypeFilter(Object.class))))
                .contains(SwitchboardDispatcher.class.getName(), SwitchboardRegistry.class.getName())
                .noneMatch(name -> name.contains("Stubs") || name.endsWith("Test"));
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
