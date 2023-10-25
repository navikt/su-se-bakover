/**
 * Konverter til og fra no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
 */
package no.nav.su.se.bakover.database.attestering

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.attestering.AttesteringDbJson

internal fun String.toAttesteringshistorikk(): Attesteringshistorikk {
    val attesteringer = deserialize<List<AttesteringDbJson>>(this)
    return Attesteringshistorikk.create(
        attesteringer.map {
            when (it) {
                is AttesteringDbJson.IverksattJson -> Attestering.Iverksatt(
                    attestant = NavIdentBruker.Attestant(it.attestant),
                    opprettet = it.opprettet,
                )

                is AttesteringDbJson.UnderkjentJson -> Attestering.Underkjent(
                    attestant = NavIdentBruker.Attestant(it.attestant),
                    opprettet = it.opprettet,
                    grunn = when (it.grunn) {
                        "INNGANGSVILKÅRENE_ER_FEILVURDERT" -> Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT
                        "BEREGNINGEN_ER_FEIL" -> Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL
                        "DOKUMENTASJON_MANGLER" -> Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER
                        "VEDTAKSBREVET_ER_FEIL" -> Attestering.Underkjent.Grunn.VEDTAKSBREVET_ER_FEIL
                        "ANDRE_FORHOLD" -> Attestering.Underkjent.Grunn.ANDRE_FORHOLD
                        else -> throw IllegalStateException("Ukjent grunn - Kunne ikke mappe ${it.grunn} til ${Attestering.Underkjent.Grunn::class.simpleName}")
                    },
                    kommentar = it.kommentar,
                )
            }
        },
    )
}
