package no.nav.su.se.bakover.test

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes

fun VedtakSomKanRevurderes.VedtakPåTidslinje.shouldBeEqualToExceptId(expected: VedtakSomKanRevurderes.VedtakPåTidslinje) {
    this.shouldBeEqualToIgnoringFields(
        expected,
        VedtakSomKanRevurderes.VedtakPåTidslinje::vilkårsvurderinger,
        VedtakSomKanRevurderes.VedtakPåTidslinje::grunnlagsdata,
    )
    this.grunnlagsdata.shouldBeEqualToExceptId(expected.grunnlagsdata)
    this.vilkårsvurderinger.shouldBeEqualToExceptId(expected.vilkårsvurderinger)
}
