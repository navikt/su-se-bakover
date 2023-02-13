package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata

sealed interface Reguleringstype {
    object AUTOMATISK : Reguleringstype {
        override fun toString() = "AUTOMATISK"
    }

    data class MANUELL(val problemer: Set<ÅrsakTilManuellRegulering>) : Reguleringstype {
        override fun toString() = "MANUELL"
    }
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

    if (this.pågåendeAvkortingEllerBehovForFremtidigAvkorting) {
        problemer.add(ÅrsakTilManuellRegulering.PågåendeAvkortingEllerBehovForFremtidigAvkorting)
    }

    return if (problemer.isNotEmpty()) Reguleringstype.MANUELL(problemer) else Reguleringstype.AUTOMATISK
}
