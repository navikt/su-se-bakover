package no.nav.su.se.bakover.domain.regulering

import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.vurderinger.domain.harForventetInntektStørreEnn0

sealed interface Reguleringstype {
    data object AUTOMATISK : Reguleringstype

    data class MANUELL(val problemer: Set<ÅrsakTilManuellRegulering>) : Reguleringstype {
        constructor(årsak: ÅrsakTilManuellRegulering) : this(setOf(årsak))
    }

    companion object {
        fun utledReguleringsTypeFrom(
            reguleringstype1: Reguleringstype,
            reguleringstype2: Reguleringstype,
        ): Reguleringstype {
            if (reguleringstype1 is MANUELL || reguleringstype2 is MANUELL) {
                return MANUELL(
                    ((reguleringstype1 as? MANUELL)?.problemer ?: emptySet()) +
                        ((reguleringstype2 as? MANUELL)?.problemer ?: emptySet()),
                )
            }
            return AUTOMATISK
        }
    }
}

fun GjeldendeVedtaksdata.utledReguleringstype(): Reguleringstype {
    val problemer = mutableSetOf<ÅrsakTilManuellRegulering>()
    if (this.harStans()) {
        problemer.add(
            ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset(
                begrunnelse = "Saken er midlertidig stanset",
            ),
        )
    }

    if (this.vilkårsvurderinger is VilkårsvurderingerRevurdering.Uføre) {
        this.vilkårsvurderinger.uføreVilkårKastHvisAlder().let {
            if (it.grunnlag.harForventetInntektStørreEnn0()) {
                problemer.add(
                    ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0(
                        begrunnelse = "Forventet inntekt er større enn 0",
                    ),
                )
            }
        }
    }

    if (this.delerAvPeriodenErOpphør()) {
        val opphørtePerioder = this.opphørtePerioderSlåttSammen()
        problemer.add(
            ÅrsakTilManuellRegulering.DelvisOpphør(
                opphørsperioder = this.opphørtePerioderSlåttSammen(),
                begrunnelse = "Saken inneholder opphørte perioder $opphørtePerioder. Disse er innenfor reguleringsperioden",
            ),
        )
    }

    if (!this.tidslinjeForVedtakErSammenhengende()) {
        problemer.add(
            ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende(
                begrunnelse = "Reguleringsperioden inneholder hull. Vi støtter ikke hull i vedtakene p.t.",
            ),
        )
    }

    return if (problemer.isNotEmpty()) Reguleringstype.MANUELL(problemer) else Reguleringstype.AUTOMATISK
}
