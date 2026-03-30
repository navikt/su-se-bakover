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

    // TODO AUTO-REG-26 Fjern
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

    // TODO AUTO-REG-26 hvorfor må dette da gjøres manuelt?? Det er vel kun om det er opphørt frem i tid som er problematisk og det skjer jo ikke?
    if (this.delerAvPeriodenErOpphør()) {
        val opphørtePerioder = this.opphørtePerioderSlåttSammen()
        problemer.add(
            ÅrsakTilManuellRegulering.DelvisOpphør(
                opphørsperioder = this.opphørtePerioderSlåttSammen(),
                begrunnelse = "Saken inneholder opphørte perioder $opphørtePerioder. Disse er innenfor reguleringsperioden",
            ),
        )
    }

    // TODO AUTO-REG-26 vil denne noen gang slå ut siden det samme sjekkes og blir  Sak.KanIkkeRegulere.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig?
    if (!this.tidslinjeForVedtakErSammenhengende()) {
        problemer.add(
            ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende(
                begrunnelse = "Reguleringsperioden inneholder hull. Vi støtter ikke hull i vedtakene p.t.",
            ),
        )
    }

    return if (problemer.isNotEmpty()) Reguleringstype.MANUELL(problemer) else Reguleringstype.AUTOMATISK
}
