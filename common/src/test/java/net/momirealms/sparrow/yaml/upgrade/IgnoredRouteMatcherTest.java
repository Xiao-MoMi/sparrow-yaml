package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.IndexElement;
import net.momirealms.sparrow.yaml.route.element.KeyElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnoredRouteMatcherTest {

    @Test
    void should_IgnoreRootAndEveryDescendant_When_EmptyRouteIsTerminal() {
        IgnoredRouteMatcher matcher = IgnoredRouteMatcher.builder()
                .add(Route.empty())
                .build();

        assertTrue(matcher.matches(Route.empty()));
        assertTrue(matcher.matches(Route.from("child")));
        assertTrue(matcher.matches(Route.from("items", 0)));
    }

    @Test
    void should_IgnoreOnlyNonRootRoutes_When_EmptyRouteDescendantsAreIgnored() {
        IgnoredRouteMatcher matcher = IgnoredRouteMatcher.builder()
                .addDescendants(Route.empty())
                .build();

        assertFalse(matcher.matches(Route.empty()));
        assertTrue(matcher.matches(Route.from("child")));
        assertTrue(matcher.matches(Route.from("items", 0)));
    }

    @Test
    void should_MatchRouteElementKindsAsPartOfTheRoute() {
        IgnoredRouteMatcher matcher = IgnoredRouteMatcher.builder()
                .add(Route.from(new KeyElement(1)))
                .add(Route.from(new IndexElement(2)))
                .build();

        assertTrue(matcher.matches(Route.from(new KeyElement(1))));
        assertTrue(matcher.matches(Route.from(new KeyElement(1), "child")));
        assertFalse(matcher.matches(Route.from(new IndexElement(1))));
        assertTrue(matcher.matches(Route.from(new IndexElement(2))));
        assertFalse(matcher.matches(Route.from(new KeyElement(2))));
    }
}
