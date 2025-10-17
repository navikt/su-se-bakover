package no.nav.su.se.bakover.statistikk.behandling.klage

import behandling.klage.domain.FormkravTilKlage

internal fun FormkravTilKlage?.toResultatBegrunnelse(): String? {
    if (this == null) return null
    return listOf(
        if (this.innenforFristen?.svar == FormkravTilKlage.Svarord.NEI) "IKKE_INNENFOR_FRISTEN" else null,
        if (this.klagesDetPåKonkreteElementerIVedtaket?.svar == false) "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET" else null,
        if (this.erUnderskrevet?.svar == FormkravTilKlage.Svarord.NEI) "IKKE_UNDERSKREVET" else null,
    ).mapNotNull { it }.joinToString(",").ifEmpty { null }
}
