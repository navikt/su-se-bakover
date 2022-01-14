package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall

internal enum class UtfallJson(val databaseType: String) {
    TRUKKET("TRUKKET"),
    RETUR("RETUR"),
    OPPHEVET("OPPHEVET"),
    MEDHOLD("MEDHOLD"),
    DELVIS_MEDHOLD("DELVIS_MEDHOLD"),
    STADFESTELSE("STADFESTELSE"),
    UGUNST("UGUNST"),
    AVVIST("AVVIST");

    fun toDomain() = when (this) {
        TRUKKET -> KlagevedtakUtfall.TRUKKET
        RETUR -> KlagevedtakUtfall.RETUR
        OPPHEVET -> KlagevedtakUtfall.OPPHEVET
        MEDHOLD -> KlagevedtakUtfall.MEDHOLD
        DELVIS_MEDHOLD -> KlagevedtakUtfall.DELVIS_MEDHOLD
        STADFESTELSE -> KlagevedtakUtfall.STADFESTELSE
        UGUNST -> KlagevedtakUtfall.UGUNST
        AVVIST -> KlagevedtakUtfall.AVVIST
    }
}

internal fun KlagevedtakUtfall.toDatabaseType() = when (this) {
    KlagevedtakUtfall.TRUKKET -> UtfallJson.TRUKKET.databaseType
    KlagevedtakUtfall.RETUR -> UtfallJson.RETUR.databaseType
    KlagevedtakUtfall.OPPHEVET -> UtfallJson.OPPHEVET.databaseType
    KlagevedtakUtfall.MEDHOLD -> UtfallJson.MEDHOLD.databaseType
    KlagevedtakUtfall.DELVIS_MEDHOLD -> UtfallJson.DELVIS_MEDHOLD.databaseType
    KlagevedtakUtfall.STADFESTELSE -> UtfallJson.STADFESTELSE.databaseType
    KlagevedtakUtfall.UGUNST -> UtfallJson.UGUNST.databaseType
    KlagevedtakUtfall.AVVIST -> UtfallJson.AVVIST.databaseType
}
