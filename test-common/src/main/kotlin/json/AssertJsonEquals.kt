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
            "Expected JSON to be similar to $expectedJson but was $this",
            expectedJson,
            this,
            // Strict array order, not object order.
            JSONCompareMode.STRICT,
        )
    } else {
        JSONAssert.assertEquals(
            expectedJson,
            this,
            CustomComparator(
                // Strict array order, not object order.
                JSONCompareMode.STRICT,
                *customizations.toTypedArray(),
            ),
        )
    }
}
