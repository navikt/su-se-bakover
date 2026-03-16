package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.LocalDate

interface ReguleringHentEksterneReguleringerService {
    fun hentEksterneReguleringer(request: HentEksterneReguleringerRequest): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>
}

/**
 * Objekt for å hente regulerte beløper som har blitt brukt som fradrag.
 * Basert på reguleringsmåned og en liste saker utledes alle brukere og eps'er som
 * har fradrag som har blitt regulert eksternt.
 *
 * TODO javadoc for hvert felt.. AI
 */
data class HentEksterneReguleringerRequest(
    val månedFørRegulering: LocalDate,
    val brukereMedEps: List<BrukerMedEps>,
) {

    data class BrukerMedEps(
        val bruker: PersonMedFradrag,
        val eps: PersonMedFradrag?,
    )

    data class PersonMedFradrag(
        val fnr: Fnr,
        val fradrag: Fradragstype,
    )

    companion object {
        fun toRequest(
            reguleringsMåned: Måned,
            forSaker: List<Sak>,
            clock: Clock,
        ): HentEksterneReguleringerRequest {
            return HentEksterneReguleringerRequest(
                månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1),
                brukereMedEps = forSaker.map { it.toBrukerMedEps(reguleringsMåned, clock) },
            )
        }

        private fun Sak.toBrukerMedEps(
            reguleringsMåned: Måned,
            clock: Clock,
        ): BrukerMedEps {
            val grunnlagsdata = hentGjeldendeVedtaksdata(reguleringsMåned, clock).getOrElse {
                throw IllegalStateException("Kan ikke hente eksterne fradrag for sak som ikke er løpende")
            }.grunnlagsdata

            val uføfreOgAlder = listOf(Fradragstype.Uføretrygd, Fradragstype.Alderspensjon)
            return BrukerMedEps(
                bruker = PersonMedFradrag(
                    fnr = fnr,
                    fradrag = grunnlagsdata.hentBrukteFradragstyperBasertPå(
                        fradragstyper = uføfreOgAlder,
                        måned = reguleringsMåned,
                        tilhører = FradragTilhører.BRUKER,
                    ).single(), // TODO bjg
                ),
                eps = grunnlagsdata.epsForMåned()[reguleringsMåned]?.let {
                    PersonMedFradrag(
                        fnr = it,
                        fradrag = grunnlagsdata.hentBrukteFradragstyperBasertPå(
                            fradragstyper = uføfreOgAlder,
                            måned = reguleringsMåned,
                            tilhører = FradragTilhører.EPS,
                        ).single(), // TODO bjg
                    )
                },
            )
        }
    }
}

data class HentingAvEksterneReguleringerFeiletForBruker(
    val fnr: Fnr,
    val alleFeil: List<FeilMedEksternRegulering>,
)

interface FeilMedEksternRegulering {
    object IngenPeriodeFraPesys : FeilMedEksternRegulering
    object ManglerPeriodeFørOgEtterReguleringFraPesys : FeilMedEksternRegulering
    object GrunnbeløpFraPesysUliktForventetGammelt : FeilMedEksternRegulering
    object GrunnbeløpFraPesysUliktForventetNytt : FeilMedEksternRegulering
    object OverlappendePeriodeFraPesys : FeilMedEksternRegulering
}

class UthentingAvPerioderUføreFeilet : IllegalStateException()
class UthentingAvPerioderAlderFeilet : IllegalStateException()
