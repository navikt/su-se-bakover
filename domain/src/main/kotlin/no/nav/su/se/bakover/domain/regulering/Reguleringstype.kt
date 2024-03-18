package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.vurderinger.domain.harForventetInntektStørreEnn0

sealed interface Reguleringstype {
    data object AUTOMATISK : Reguleringstype

    data class MANUELL(val problemer: Set<ÅrsakTilManuellRegulering>) : Reguleringstype
}

fun GjeldendeVedtaksdata.utledReguleringstype(
    supplementForBruker: ReguleringssupplementFor?,
    supplement: Reguleringssupplement,
): Reguleringstype {
    val problemer = mutableSetOf<ÅrsakTilManuellRegulering>()
    val epsFnrForMåned: Map<Måned, Fnr> = this.grunnlagsdata.epsForMåned()
    this.grunnlagsdata.fradragsgrunnlag.filterNot {
        val fradragsperiode = it.fradrag.periode
        if (it.fradrag.tilhørerBruker()) {
            supplementForBruker?.inneholderFradragForTypeOgPeriode(it.fradragstype, fradragsperiode) ?: false
        } else {
            fradragsperiode.måneder().all { måned ->
                epsFnrForMåned[måned]?.let { epsFnr ->
                    supplement.getFor(epsFnr)?.inneholderFradragForTypeOgMåned(it.fradragstype, måned)
                } ?: false
            }
        }
    }.let {
        if (it.any { it.fradrag.skalJusteresVedGEndring() }) {
            problemer.add(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt)
        }
    }

    if (this.harStans()) {
        problemer.add(ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset)
    }

    this.vilkårsvurderinger.uføreVilkårKastHvisAlder().let {
        if (it.grunnlag.harForventetInntektStørreEnn0()) {
            problemer.add(ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0)
        }
    }

    if (this.delerAvPeriodenErOpphør()) {
        problemer.add(ÅrsakTilManuellRegulering.DelvisOpphør)
    }

    if (!this.tidslinjeForVedtakErSammenhengende()) {
        problemer.add(ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende)
    }

    return if (problemer.isNotEmpty()) Reguleringstype.MANUELL(problemer) else Reguleringstype.AUTOMATISK
}
