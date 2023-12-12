package no.nav.su.se.bakover.test.json

import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

fun String.shouldBeSimilarJsonTo(expectedJson: String, vararg ignoredPaths: String) {
    val customizations = ignoredPaths.map {
        Customization(
            it,
        ) { _, _ -> true }
    }
    if (customizations.isEmpty()) {
        JSONAssert.assertEquals(
            this,
            expectedJson,
            JSONCompareMode.STRICT,
        )
    } else {
        JSONAssert.assertEquals(
            this,
            expectedJson,
            CustomComparator(
                JSONCompareMode.STRICT,
                *customizations.toTypedArray(),
            ),
        )
    }
}
