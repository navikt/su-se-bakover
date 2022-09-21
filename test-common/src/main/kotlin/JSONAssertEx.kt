package no.nav.su.se.bakover.test

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
}
