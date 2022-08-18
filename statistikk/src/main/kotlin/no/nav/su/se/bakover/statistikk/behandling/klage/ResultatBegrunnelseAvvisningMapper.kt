package no.nav.su.se.bakover.statistikk.behandling.klage

import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage

internal fun VilkårsvurderingerTilKlage?.toResultatBegrunnelse(): String? {
    if (this == null) return null
    return listOf(
        if (this.innenforFristen == VilkårsvurderingerTilKlage.Svarord.NEI) "IKKE_INNENFOR_FRISTEN" else null,
        if (this.klagesDetPåKonkreteElementerIVedtaket == false) "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET" else null,
        if (this.erUnderskrevet == VilkårsvurderingerTilKlage.Svarord.NEI) "IKKE_UNDERSKREVET" else null,
    ).mapNotNull { it }.joinToString(",").ifEmpty { null }
}
