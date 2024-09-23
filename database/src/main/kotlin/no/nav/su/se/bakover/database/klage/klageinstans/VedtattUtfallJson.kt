package no.nav.su.se.bakover.database.klage.klageinstans

import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall

internal enum class UtfallJson {
    TRUKKET,
    RETUR,
    OPPHEVET,
    MEDHOLD,
    DELVIS_MEDHOLD,
    STADFESTELSE,
    UGUNST,
    AVVIST,
    HENVIST,
    ;

    fun toDomain() = when (this) {
        TRUKKET -> AvsluttetKlageinstansUtfall.TilInformasjon.Trukket
        RETUR -> AvsluttetKlageinstansUtfall.Retur
        OPPHEVET -> AvsluttetKlageinstansUtfall.KreverHandling.Opphevet
        MEDHOLD -> AvsluttetKlageinstansUtfall.KreverHandling.Medhold
        DELVIS_MEDHOLD -> AvsluttetKlageinstansUtfall.KreverHandling.DelvisMedhold
        STADFESTELSE -> AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse
        UGUNST -> AvsluttetKlageinstansUtfall.KreverHandling.Ugunst
        AVVIST -> AvsluttetKlageinstansUtfall.TilInformasjon.Avvist
        HENVIST -> AvsluttetKlageinstansUtfall.TilInformasjon.Henvist
    }
}

internal fun AvsluttetKlageinstansUtfall.toDatabaseType(): String {
    return when (this) {
        is AvsluttetKlageinstansUtfall.TilInformasjon.Trukket -> UtfallJson.TRUKKET
        is AvsluttetKlageinstansUtfall.Retur -> UtfallJson.RETUR
        is AvsluttetKlageinstansUtfall.KreverHandling.Opphevet -> UtfallJson.OPPHEVET
        is AvsluttetKlageinstansUtfall.KreverHandling.Medhold -> UtfallJson.MEDHOLD
        is AvsluttetKlageinstansUtfall.KreverHandling.DelvisMedhold -> UtfallJson.DELVIS_MEDHOLD
        is AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse -> UtfallJson.STADFESTELSE
        is AvsluttetKlageinstansUtfall.KreverHandling.Ugunst -> UtfallJson.UGUNST
        is AvsluttetKlageinstansUtfall.TilInformasjon.Avvist -> UtfallJson.AVVIST
        is AvsluttetKlageinstansUtfall.TilInformasjon.Henvist -> UtfallJson.HENVIST
    }.toString()
}
