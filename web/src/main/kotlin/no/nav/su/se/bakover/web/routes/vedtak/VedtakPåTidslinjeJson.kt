package no.nav.su.se.bakover.web.routes.vedtak

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje

internal data class VedtakPåTidslinjeJson(
    val periode: PeriodeJson,
    val vedtakId: String,
    val vedtakType: VedtakTypeJson,
)

internal fun List<VedtakPåTidslinje>.toJson(): List<VedtakPåTidslinjeJson> {
    return this.map {
        VedtakPåTidslinjeJson(
            periode = it.periode.toJson(),
            vedtakId = it.originaltVedtak.id.toString(),
            vedtakType = it.originaltVedtak.toVedtakTypeJson(),
        )
    }
}
