package no.nav.su.se.bakover.test

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import no.nav.su.se.bakover.domain.vedtak.Vedtak

fun Vedtak.VedtakPåTidslinje.shouldBeEqualToExceptId(expected: Vedtak.VedtakPåTidslinje) {
    this.shouldBeEqualToIgnoringFields(
        expected,
        Vedtak.VedtakPåTidslinje::vilkårsvurderinger,
        Vedtak.VedtakPåTidslinje::grunnlagsdata,
    )
    this.grunnlagsdata.shouldBeEqualToExceptId(expected.grunnlagsdata)
    this.vilkårsvurderinger.shouldBeEqualToExceptId(expected.vilkårsvurderinger)
}
