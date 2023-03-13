package no.nav.su.se.bakover.test

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje

fun VedtakPåTidslinje.shouldBeEqualToExceptId(expected: VedtakPåTidslinje) {
    this.shouldBeEqualToIgnoringFields(
        expected,
        VedtakPåTidslinje::vilkårsvurderinger,
        VedtakPåTidslinje::grunnlagsdata,
    )
    this.grunnlagsdata.shouldBeEqualToExceptId(expected.grunnlagsdata)
    this.vilkårsvurderinger.shouldBeEqualToExceptId(expected.vilkårsvurderinger)
}
