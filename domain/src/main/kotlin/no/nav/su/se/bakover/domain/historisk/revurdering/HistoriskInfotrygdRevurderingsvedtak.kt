package no.nav.su.se.bakover.domain.historisk.revurdering

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.util.UUID

data class HistoriskInfotrygdRevurderingsvedtak(
    val id: HistoriskInfotrygdRevurderingsvedtakId,
    val revurderingId: HistoriskInfotrygdRevurderingId,
    val sakId: UUID,
    val utbetalingId: UUID30,
    val iverksatt: Tidspunkt,
    val attestant: NavIdentBruker.Attestant,
    val beregning: HistoriskInfotrygdBeregning,
) {
    val periode: Periode = beregning.månedsresultater.keys.let {
        Periode.create(it.first().fraOgMed, it.last().tilOgMed)
    }

    fun effekt() = HistoriskInfotrygdRevurderingseffekt(
        vedtakId = id,
        iverksatt = iverksatt,
        månedsresultater = beregning.månedsresultater,
    )
}
