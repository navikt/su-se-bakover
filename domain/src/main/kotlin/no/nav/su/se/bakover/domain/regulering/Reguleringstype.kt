package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.vurderinger.domain.harForventetInntektStørreEnn0

sealed interface Reguleringstype {
    data object AUTOMATISK : Reguleringstype

    data class MANUELL(val problemer: Set<ÅrsakTilManuellRegulering>) : Reguleringstype
}

fun GjeldendeVedtaksdata.utledReguleringstype(): Reguleringstype {
    val problemer = mutableSetOf<ÅrsakTilManuellRegulering>()

    if (this.grunnlagsdata.fradragsgrunnlag.any { it.fradrag.skalJusteresVedGEndring() }) {
        problemer.add(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt)
    }

    if (this.harStans()) {
        problemer.add(ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset)
    }

    this.vilkårsvurderinger.uføreVilkår().fold(
        {
            TODO("vilkårsvurdering_alder implementer regulering for alder")
        },
        {
            if (it.grunnlag.harForventetInntektStørreEnn0()) {
                problemer.add(ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0)
            }
        },
    )

    if (this.delerAvPeriodenErOpphør()) {
        problemer.add(ÅrsakTilManuellRegulering.DelvisOpphør)
    }

    if (!this.tidslinjeForVedtakErSammenhengende()) {
        problemer.add(ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende)
    }

    return if (problemer.isNotEmpty()) Reguleringstype.MANUELL(problemer) else Reguleringstype.AUTOMATISK
}
