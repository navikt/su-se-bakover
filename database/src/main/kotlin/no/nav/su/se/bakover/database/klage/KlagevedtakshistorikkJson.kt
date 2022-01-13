package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.Klagevedtakshistorikk

internal fun Klagevedtakshistorikk.toDatabaseJson(): String {
    return this.map {
        VedtattUtfallJson(
            it.opprettet,
            when (it.utfall) {
                KlagevedtakUtfall.TRUKKET -> VedtattUtfallJson.UtfallJson.TRUKKET
                KlagevedtakUtfall.RETUR -> VedtattUtfallJson.UtfallJson.RETUR
                KlagevedtakUtfall.OPPHEVET -> VedtattUtfallJson.UtfallJson.OPPHEVET
                KlagevedtakUtfall.MEDHOLD -> VedtattUtfallJson.UtfallJson.MEDHOLD
                KlagevedtakUtfall.DELVIS_MEDHOLD -> VedtattUtfallJson.UtfallJson.DELVIS_MEDHOLD
                KlagevedtakUtfall.STADFESTELSE -> VedtattUtfallJson.UtfallJson.STADFESTELSE
                KlagevedtakUtfall.UGUNST -> VedtattUtfallJson.UtfallJson.UGUNST
                KlagevedtakUtfall.AVVIST -> VedtattUtfallJson.UtfallJson.AVVIST
            }
        )
    }.serialize()
}
