package no.nav.su.se.bakover.test.xml

import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.ComparisonControllers
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.Diff
import org.xmlunit.diff.ElementSelectors

infix fun String.shouldBeSimilarXmlTo(expectedXml: String) {
    val diff = this.compareXmlWith(expectedXml)
    if (diff.hasDifferences()) {
        val differences = diff.differences.joinToString("\n") { it.toString() }
        throw AssertionError("Expected XMLs to be similar, but found differences:\n$differences")
    }
}

private fun String.compareXmlWith(expectedXml: String): Diff {
    return DiffBuilder.compare(this).withTest(expectedXml)
        .ignoreWhitespace()
        .withNodeMatcher(DefaultNodeMatcher(ElementSelectors.byNameAndText))
        .withComparisonController(ComparisonControllers.StopWhenDifferent)
        .checkForSimilar()
        .build()
}
