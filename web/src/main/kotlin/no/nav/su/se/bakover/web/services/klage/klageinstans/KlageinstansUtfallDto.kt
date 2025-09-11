package no.nav.su.se.bakover.web.services.klage.klageinstans

import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall

enum class KlageinstansUtfallDto {
    TRUKKET,
    RETUR,
    OPPHEVET,
    MEDHOLD,
    DELVIS_MEDHOLD,
    STADFESTELSE,
    UGUNST,
    AVVIST,
    HENVIST,
    HENLAGT,
    ;

    fun toDomain(): AvsluttetKlageinstansUtfall = when (this) {
        TRUKKET -> AvsluttetKlageinstansUtfall.TilInformasjon.Trukket
        RETUR -> AvsluttetKlageinstansUtfall.Retur
        OPPHEVET -> AvsluttetKlageinstansUtfall.KreverHandling.Opphevet
        MEDHOLD -> AvsluttetKlageinstansUtfall.KreverHandling.Medhold
        DELVIS_MEDHOLD -> AvsluttetKlageinstansUtfall.KreverHandling.DelvisMedhold
        STADFESTELSE -> AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse
        UGUNST -> AvsluttetKlageinstansUtfall.KreverHandling.Ugunst
        AVVIST -> AvsluttetKlageinstansUtfall.TilInformasjon.Avvist
        HENVIST -> AvsluttetKlageinstansUtfall.TilInformasjon.Henvist
        HENLAGT -> AvsluttetKlageinstansUtfall.TilInformasjon.Henlagt
    }
}
