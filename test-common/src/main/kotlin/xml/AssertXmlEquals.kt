package no.nav.su.se.bakover.test.xml

import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.ComparisonControllers
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.Diff
import org.xmlunit.diff.ElementSelectors

infix fun String.shouldBeSimilarXmlTo(expectedXml: String) {
    return shouldBeSimilarXmlTo(expectedXml, false)
}

fun String.shouldBeSimilarXmlTo(expectedXml: String, strict: Boolean = false) {
    val diff = this.compareXmlWith(expectedXml, strict)
    if (diff.hasDifferences()) {
        val differences = diff.differences.joinToString("\n") { it.toString() }
        throw AssertionError("Expected XMLs to be similar, but found differences:\n$differences")
    }
}

private fun String.compareXmlWith(expectedXml: String, strict: Boolean): Diff {
    val diffBuilder = DiffBuilder.compare(this).withTest(expectedXml)
        .ignoreWhitespace()
        .withNodeMatcher(DefaultNodeMatcher(ElementSelectors.byNameAndText))
        .withComparisonController(ComparisonControllers.StopWhenDifferent)

    if (strict) {
        diffBuilder.withNodeMatcher(DefaultNodeMatcher(ElementSelectors.Default))
    }

    return diffBuilder.checkForSimilar().build()
}
