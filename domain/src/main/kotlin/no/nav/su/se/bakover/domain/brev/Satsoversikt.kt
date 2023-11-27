package no.nav.su.se.bakover.domain.brev

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.common.extensions.ddMMyyyyFormatter
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.utledBeregningsstrategi
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.fullstendigOrThrow
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import sats.domain.Satskategori
import java.time.LocalDate

/**
 * DTO for brev til su-pdfgen
 */
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
            // TODO jah: Vi har det først som en LocalDate, formaterer til en string, for deretter og formatere det på nytt!?
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
            val nyPeriode = periode.slåSammen(other.periode).getOrElse { throw IllegalArgumentException() }
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
        fun fra(søknadsbehandling: Søknadsbehandling, satsFactory: SatsFactory): Satsoversikt {
            return fra(søknadsbehandling.grunnlagsdata.bosituasjon, satsFactory, søknadsbehandling.sakstype)
        }

        fun fra(revurdering: Revurdering, satsFactory: SatsFactory): Satsoversikt {
            return fra(revurdering.grunnlagsdata.bosituasjon, satsFactory, revurdering.sakstype)
        }

        fun fra(bosituasjoner: List<Grunnlag.Bosituasjon>, satsFactory: SatsFactory, sakstype: Sakstype): Satsoversikt {
            return bosituasjoner
                .map { it.fullstendigOrThrow() }
                .flatMap { bosituasjon ->
                    bosituasjon.periode.måneder()
                        .map { måned -> måned to bosituasjon }
                        .map { (måned, bosituasjon) ->
                            val (strategi, sats) = bosituasjon.utledBeregningsstrategi(satsFactory, sakstype)
                                .let { it to it.beregn(måned) }
                            Satsperiode(
                                fraOgMed = måned.fraOgMed.ddMMyyyy(),
                                tilOgMed = måned.tilOgMed.ddMMyyyy(),
                                sats = sats.satskategori.toJsonstring(),
                                satsBeløp = sats.satsForMånedAvrundet,
                                satsGrunn = strategi.satsgrunn().toString(),
                            )
                        }
                }.let {
                    Satsoversikt(
                        it.slåSammenLikePerioder()
                            .sortedBy { LocalDate.parse(it.fraOgMed, ddMMyyyyFormatter) },
                    )
                }
        }

        private fun Satskategori.toJsonstring(): String {
            return when (this) {
                Satskategori.ORDINÆR -> "ordinær"
                Satskategori.HØY -> "høy"
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
