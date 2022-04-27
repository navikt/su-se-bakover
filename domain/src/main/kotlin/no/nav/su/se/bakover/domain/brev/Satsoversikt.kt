package no.nav.su.se.bakover.domain.brev

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.common.ddMMyyyyFormatter
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.utledBeregningsstrategi
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.fullstendigOrThrow
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.LocalDate

data class Satsoversikt(
    val perioder: List<Satsperiode>,
) {
    data class Satsperiode(
        val fraOgMed: String,
        val tilOgMed: String,
        val sats: String,
        val satsBeløp: Int,
        val satsGrunn: String,
    ) {
        private val periode = Periode.create(
            fraOgMed = LocalDate.parse(fraOgMed, ddMMyyyyFormatter),
            tilOgMed = LocalDate.parse(tilOgMed, ddMMyyyyFormatter),
        )

        fun tilstøterOgErLik(other: Satsperiode): Boolean {
            return periode.tilstøter(other.periode) &&
                sats == other.sats &&
                satsBeløp == other.satsBeløp &&
                satsGrunn == other.satsGrunn
        }

        fun slåSammen(other: Satsperiode): Satsperiode {
            if (!tilstøterOgErLik(other)) throw IllegalArgumentException()
            val nyPeriode = periode.slåSammen(other.periode).getOrHandle { throw IllegalArgumentException() }
            return Satsperiode(
                fraOgMed = nyPeriode.fraOgMed.ddMMyyyy(),
                tilOgMed = nyPeriode.tilOgMed.ddMMyyyy(),
                sats = sats,
                satsBeløp = satsBeløp,
                satsGrunn = satsGrunn,
            )
        }
    }

    companion object {
        fun fra(søknadsbehandling: Søknadsbehandling): Satsoversikt {
            return fra(søknadsbehandling.grunnlagsdata.bosituasjon)
        }

        fun fra(revurdering: Revurdering): Satsoversikt {
            return fra(revurdering.grunnlagsdata.bosituasjon)
        }

        fun fra(bosituasjoner: List<Grunnlag.Bosituasjon>): Satsoversikt {
            return bosituasjoner
                .map { it.fullstendigOrThrow() }
                .flatMap { bosituasjon ->
                    bosituasjon.periode.tilMånedsperioder()
                        .map { måned -> måned to bosituasjon }
                        .map { (måned, bosituasjon) ->
                            Satsperiode(
                                fraOgMed = måned.fraOgMed.ddMMyyyy(),
                                tilOgMed = måned.tilOgMed.ddMMyyyy(),
                                sats = bosituasjon.utledBeregningsstrategi().sats().toString().lowercase(),
                                satsBeløp = bosituasjon.utledBeregningsstrategi().sats()
                                    .månedsbeløpSomHeltall(måned.fraOgMed),
                                satsGrunn = bosituasjon.utledBeregningsstrategi().satsgrunn().toString(),
                            )
                        }
                }.let {
                    Satsoversikt(it.slåSammenLikePerioder().sortedBy { LocalDate.parse(it.fraOgMed, ddMMyyyyFormatter) })
                }
        }

        fun List<Satsperiode>.slåSammenLikePerioder(): List<Satsperiode> {
            return fold(mutableListOf()) { acc, satsperiode ->
                if (acc.isEmpty()) {
                    acc.add(satsperiode)
                } else if (acc.last().tilstøterOgErLik(satsperiode)) {
                    val last = acc.removeLast()
                    acc.add(last.slåSammen(satsperiode))
                } else {
                    acc.add(satsperiode)
                }
                acc
            }
        }
    }
}
