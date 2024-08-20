package no.nav.su.se.bakover.database.klage.klageinstans

import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall

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
        TRUKKET -> AvsluttetKlageinstansUtfall.TRUKKET
        RETUR -> AvsluttetKlageinstansUtfall.RETUR
        OPPHEVET -> AvsluttetKlageinstansUtfall.OPPHEVET
        MEDHOLD -> AvsluttetKlageinstansUtfall.MEDHOLD
        DELVIS_MEDHOLD -> AvsluttetKlageinstansUtfall.DELVIS_MEDHOLD
        STADFESTELSE -> AvsluttetKlageinstansUtfall.STADFESTELSE
        UGUNST -> AvsluttetKlageinstansUtfall.UGUNST
        AVVIST -> AvsluttetKlageinstansUtfall.AVVIST
    }
}

internal fun AvsluttetKlageinstansUtfall.toDatabaseType() = when (this) {
    AvsluttetKlageinstansUtfall.TRUKKET -> UtfallJson.TRUKKET.databaseType
    AvsluttetKlageinstansUtfall.RETUR -> UtfallJson.RETUR.databaseType
    AvsluttetKlageinstansUtfall.OPPHEVET -> UtfallJson.OPPHEVET.databaseType
    AvsluttetKlageinstansUtfall.MEDHOLD -> UtfallJson.MEDHOLD.databaseType
    AvsluttetKlageinstansUtfall.DELVIS_MEDHOLD -> UtfallJson.DELVIS_MEDHOLD.databaseType
    AvsluttetKlageinstansUtfall.STADFESTELSE -> UtfallJson.STADFESTELSE.databaseType
    AvsluttetKlageinstansUtfall.UGUNST -> UtfallJson.UGUNST.databaseType
    AvsluttetKlageinstansUtfall.AVVIST -> UtfallJson.AVVIST.databaseType
}
