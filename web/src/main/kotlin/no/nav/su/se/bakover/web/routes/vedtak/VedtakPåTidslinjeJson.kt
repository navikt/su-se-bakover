package no.nav.su.se.bakover.web.routes.vedtak

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes

internal data class VedtakPåTidslinjeJson(
    val periode: PeriodeJson,
    val vedtakId: String,
    val vedtakType: VedtakTypeJson,
)

internal fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.toJson(): List<VedtakPåTidslinjeJson> {
    return this.map {
        VedtakPåTidslinjeJson(
            periode = it.periode.toJson(),
            vedtakId = it.originaltVedtak.id.toString(),
            vedtakType = it.originaltVedtak.toVedtakTypeJson(),
        )
    }
}
