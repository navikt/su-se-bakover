package no.nav.su.se.bakover.domain.revurdering.revurderes

import arrow.core.Either
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vedtak.domain.VedtakSomKanRevurderes
import java.time.Clock
import java.util.UUID

/**
 * @param value Map med måned til vedtakId
 * For hver måned vi revurderer, tar vi utgangspunkt i den aktuelle (nyeste) vedtaksmåneden (kun ID for å spare ressurser).
 * Skal på sikt erstatte [no.nav.su.se.bakover.domain.revurdering.Revurdering.tilRevurdering] feltet.
 */
data class VedtakSomRevurderesMånedsvis(
    val value: Map<Måned, UUID>,
) : Map<Måned, UUID> by value

fun GjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(): VedtakSomRevurderesMånedsvis {
    return this.gjeldendeVedtakMånedsvisMedPotensielleHull()
        .toVedtakSomRevurderesMånedsvis()
}

private fun Map<Måned, VedtakSomKanRevurderes>.toVedtakSomRevurderesMånedsvis(): VedtakSomRevurderesMånedsvis {
    return VedtakSomRevurderesMånedsvis(this.mapValues { it.value.id })
}

/**
 * Henter vedtak som revurderes månedsvis for en gitt revurderingsperiode.
 * I denne revurderings-konteksten vil perioden og måneder som representeres være 1-1.
 */
fun Sak.vedtakSomRevurderesMånedsvis(
    periode: Periode,
    clock: Clock,
    revurderingsÅrsak: Revurderingsårsak.Årsak,
): Either<Sak.GjeldendeVedtaksdataErUgyldigForRevurdering, VedtakSomRevurderesMånedsvis> {
    return hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = periode,
        revurderingsÅrsak = revurderingsÅrsak,
        clock = clock,
    ).map {
        it.toVedtakSomRevurderesMånedsvis()
    }.onRight {
        require(periode.måneder() == it.value.keys.toList()) {
            "Mismatch i periode $periode og måneder som revurderes ${it.value}"
        }
    }
}
