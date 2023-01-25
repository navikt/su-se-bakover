package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.util.UUID

/**
 * @param value Map med måned til vedtakId
 * For hver måned en revurdering revurderer, tar vi utgangspunkt i den aktuelle vedtaksmåneden (kun ID for å spare ressurser).
 * Skal på sikt erstatte [Revurdering.tilRevurdering] feltet.
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
