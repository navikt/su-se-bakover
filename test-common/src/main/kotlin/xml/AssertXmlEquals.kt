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

    // sjekker at innholdet i XML'en er lik, og gir en brukbar feilmelding, dersom det er feil
    if (diff.first.hasDifferences()) {
        val differences = diff.first.differences.joinToString("\n") { it.toString() }
        throw AssertionError("Expected XMLs to be similar, but found differences:\n$differences")
    }

    // sjekker at strukturen i XML'en er lik
    if (diff.second.hasDifferences()) {
        val differences = diff.second.differences.joinToString("\n") { it.toString() }
        throw AssertionError("Expected XMLs to be similar, but found differences:\n$differences")
    }
}

/**
 * Dersom innholdet (selve daten) i XML'en er feil, vil builderen med node-matcher gi en feilmelding som ikke er til hjelp.
 * Derfor lager vi en builder uten node-matcher, som kan brukes for data-sjekk
 * og en med node-matcher som kan brukes for å sjekke at strukturen er lik.
 *
 * Kan si at builderen med node-matcher vil reagere på hvis dataen er feil - men builderen uten node matcher gir ikke feil om strukturen er feil
 */
private fun String.compareXmlWith(expectedXml: String, strict: Boolean): Pair<Diff, Diff> {
    val diffBuilderWithoutNodeMatcher = DiffBuilder.compare(this).withTest(expectedXml)
        .ignoreWhitespace()
        .withComparisonController(ComparisonControllers.StopWhenDifferent)

    val diffBuilderWithNodeMatcher = DiffBuilder.compare(this).withTest(expectedXml)
        .ignoreWhitespace()
        .withNodeMatcher(DefaultNodeMatcher(ElementSelectors.byNameAndText))
        .withComparisonController(ComparisonControllers.StopWhenDifferent)

    if (strict) {
        diffBuilderWithNodeMatcher.withNodeMatcher(DefaultNodeMatcher(ElementSelectors.Default))
    }

    return Pair(
        diffBuilderWithoutNodeMatcher.checkForSimilar().build(),
        diffBuilderWithNodeMatcher.checkForSimilar().build(),
    )
}
