package no.nav.su.se.bakover.statistikk.behandling.klage

import Klageavvisningsgrunn
import behandling.klage.domain.FormkravTilKlage

internal fun FormkravTilKlage?.toResultatBegrunnelse(): String? {
    if (this == null) return null
    return listOf(
        if (this.innenforFristen?.svar == FormkravTilKlage.Svarord.NEI) {
            Klageavvisningsgrunn.IKKE_INNENFOR_FRISTEN.name
        } else {
            null
        },
        if (this.klagesDetPåKonkreteElementerIVedtaket?.svar == false) {
            Klageavvisningsgrunn.KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET.name
        } else {
            null
        },
        if (this.erUnderskrevet?.svar == FormkravTilKlage.Svarord.NEI) {
            Klageavvisningsgrunn.IKKE_UNDERSKREVET.name
        } else {
            null
        },
    ).mapNotNull { it }.joinToString(",").ifEmpty { null }
}
