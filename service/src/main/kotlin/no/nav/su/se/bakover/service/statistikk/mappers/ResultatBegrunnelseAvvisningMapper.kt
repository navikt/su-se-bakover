package no.nav.su.se.bakover.service.statistikk.mappers

import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage

fun VilkårsvurderingerTilKlage.Utfylt.mapToResultatBegrunnelse(): String? {
    return listOf(
        if (this.innenforFristen == VilkårsvurderingerTilKlage.Svarord.NEI) "IKKE_INNENFOR_FRISTEN" else null,
        if (!this.klagesDetPåKonkreteElementerIVedtaket) "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET" else null,
        if (this.erUnderskrevet == VilkårsvurderingerTilKlage.Svarord.NEI) "IKKE_UNDERSKREVET" else null,
    ).mapNotNull { it }.joinToString(",").ifEmpty { null }
}
