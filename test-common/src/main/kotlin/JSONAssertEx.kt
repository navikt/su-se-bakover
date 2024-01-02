package no.nav.su.se.bakover.test

import org.json.JSONObject
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

/**
 * @param ignorePaths eg. søknader[*].id or id
 */
fun jsonAssertEquals(
    expected: String,
    actual: String,
    vararg ignorePaths: String,
) {
    try {
        JSONAssert.assertEquals(
            expected,
            actual,
            CustomComparator(
                JSONCompareMode.STRICT,
                *ignorePaths.map {
                    Customization(
                        it,
                    ) { _, _ -> true }
                }.toTypedArray(),
            ),
        )
    } catch (error: AssertionError) {
        throw AssertionError(
            """
            |JSONAssert.assertEquals failed:
            |
            |Expected: ${JSONObject(expected).toString(2)}
            |
            |Actual: ${JSONObject(actual).toString(2)}
            |
            |Error: ${error.message}
            """.trimMargin(),
        )
    }
}
