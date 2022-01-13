package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.Klagevedtakshistorikk
import no.nav.su.se.bakover.domain.klage.VedtattUtfall

internal fun Klagevedtakshistorikk.toDatabaseJson(): String {
    return this.map {
        VedtattUtfallJson(
            it.opprettet,
            when (it.klagevedtakUtfall) {
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

internal fun String.toKlagevedtakshistorikk(): Klagevedtakshistorikk {
    val vedtattUtfall = deserialize<List<VedtattUtfallJson>>(this)

    return Klagevedtakshistorikk.create(
        vedtattUtfall = vedtattUtfall.map {
            VedtattUtfall(
                klagevedtakUtfall = when (it.utfallJson) {
                    VedtattUtfallJson.UtfallJson.TRUKKET -> KlagevedtakUtfall.TRUKKET
                    VedtattUtfallJson.UtfallJson.RETUR -> KlagevedtakUtfall.RETUR
                    VedtattUtfallJson.UtfallJson.OPPHEVET -> KlagevedtakUtfall.OPPHEVET
                    VedtattUtfallJson.UtfallJson.MEDHOLD -> KlagevedtakUtfall.MEDHOLD
                    VedtattUtfallJson.UtfallJson.DELVIS_MEDHOLD -> KlagevedtakUtfall.DELVIS_MEDHOLD
                    VedtattUtfallJson.UtfallJson.STADFESTELSE -> KlagevedtakUtfall.STADFESTELSE
                    VedtattUtfallJson.UtfallJson.UGUNST -> KlagevedtakUtfall.UGUNST
                    VedtattUtfallJson.UtfallJson.AVVIST -> KlagevedtakUtfall.AVVIST
                },
                opprettet = it.opprettet,
            )
        },
    )
}
