package no.nav.su.se.bakover.database.klage.klageinstans

import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall

internal enum class UtfallJson(val databaseType: String) {
    TRUKKET("TRUKKET"),
    RETUR("RETUR"),
    OPPHEVET("OPPHEVET"),
    MEDHOLD("MEDHOLD"),
    DELVIS_MEDHOLD("DELVIS_MEDHOLD"),
    STADFESTELSE("STADFESTELSE"),
    UGUNST("UGUNST"),
    AVVIST("AVVIST"),
    ;

    fun toDomain() = when (this) {
        TRUKKET -> KlageinstansUtfall.TRUKKET
        RETUR -> KlageinstansUtfall.RETUR
        OPPHEVET -> KlageinstansUtfall.OPPHEVET
        MEDHOLD -> KlageinstansUtfall.MEDHOLD
        DELVIS_MEDHOLD -> KlageinstansUtfall.DELVIS_MEDHOLD
        STADFESTELSE -> KlageinstansUtfall.STADFESTELSE
        UGUNST -> KlageinstansUtfall.UGUNST
        AVVIST -> KlageinstansUtfall.AVVIST
    }
}

internal fun KlageinstansUtfall.toDatabaseType() = when (this) {
    KlageinstansUtfall.TRUKKET -> UtfallJson.TRUKKET.databaseType
    KlageinstansUtfall.RETUR -> UtfallJson.RETUR.databaseType
    KlageinstansUtfall.OPPHEVET -> UtfallJson.OPPHEVET.databaseType
    KlageinstansUtfall.MEDHOLD -> UtfallJson.MEDHOLD.databaseType
    KlageinstansUtfall.DELVIS_MEDHOLD -> UtfallJson.DELVIS_MEDHOLD.databaseType
    KlageinstansUtfall.STADFESTELSE -> UtfallJson.STADFESTELSE.databaseType
    KlageinstansUtfall.UGUNST -> UtfallJson.UGUNST.databaseType
    KlageinstansUtfall.AVVIST -> UtfallJson.AVVIST.databaseType
}
