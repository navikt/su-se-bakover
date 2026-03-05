package no.nav.su.se.bakover.service.regulering

import arrow.core.getOrElse
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.NyttFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.RegulerteFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.SakerMedRegulerteFradragEksternKilde
import java.time.Clock
import java.time.LocalDate

// TODO AUTO-REG-26  WIP

data class HentEksterneReguleringerCommand(
    val månedFørRegulering: LocalDate,
    val brukereMedEpsUføre: List<BrukerMedEps>,
    val brukereMedEpsAlder: List<BrukerMedEps>,
) {
    companion object {
        fun toCommand(
            reguleringsMåned: Måned,
            saker: List<Sak>,
            clock: Clock,
        ): HentEksterneReguleringerCommand {
            // TODO AUTO-REG-26 - Maks 50 fnr per kall
            val (uføreSaker, alderSaker) = saker.partition { it.type == Sakstype.UFØRE }
            val toBrukerMedEps = { sak: Sak ->
                BrukerMedEps(
                    fnr = sak.fnr,
                    sakstype = sak.type,
                    eps = sak.hentGjeldendeVedtaksdata(reguleringsMåned, clock).getOrNull()?.grunnlagsdata?.eps
                        ?: emptyList(),
                )
            }
            return HentEksterneReguleringerCommand(
                månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1),
                brukereMedEpsUføre = uføreSaker.map(toBrukerMedEps),
                brukereMedEpsAlder = alderSaker.map(toBrukerMedEps),
            )
        }
    }
}

data class BrukerMedEps(
    val fnr: Fnr,
    val sakstype: Sakstype,
    val eps: List<Fnr>,
)

class ReguleringHentEksterneReguleringerService(private val pesysClient: PesysClient) {

    fun hentEksterneReguleringer(command: HentEksterneReguleringerCommand): SakerMedRegulerteFradragEksternKilde {
        // TODO feilhåndtering..

        // TODO Må resultat fra uføre og alder mappes til et fnr? Trolig ja, spesielt når vi henter aap ??

        val fnrListUføre = command.brukereMedEpsUføre.flatMap { listOf(it.fnr) + it.eps }.distinct()
        val uførePerioder = pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = fnrListUføre,
            dato = command.månedFørRegulering,
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat

        // TODO bjg mer robust logikk for dette so forsikret om kun to perioder hvor før og etter regulering

        val uførefradrag = command.brukereMedEpsUføre.map { brukerMedEps ->
            val eksterneFradragBruker = uførePerioder.singleOrNull { Fnr(it.fnr) == brukerMedEps.fnr }
                ?: throw IllegalStateException("Noe gikk galt fiks bedre feilmelding")

            val eskterneFradragEps = uførePerioder.filter { brukerMedEps.eps.contains(Fnr(it.fnr)) }

            RegulerteFradragEksternKilde(
                bruker = NyttFradragEksternKilde(
                    fnr = brukerMedEps.fnr,
                    førRegulering = eksterneFradragBruker.perioder[0].netto,
                    etterRegulering = eksterneFradragBruker.perioder[1].netto,
                ),
                forEps = eskterneFradragEps.map { eksterntFradragEps ->
                    NyttFradragEksternKilde(
                        fnr = Fnr(eksterntFradragEps.fnr),
                        førRegulering = eksterntFradragEps.perioder[0].netto,
                        etterRegulering = eksterntFradragEps.perioder[1].netto,
                    )
                },
            )
        }

        val fnrListAlder = command.brukereMedEpsAlder.flatMap { listOf(it.fnr) + it.eps }.distinct()
        val alderPerioder = pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = fnrListAlder,
            dato = command.månedFørRegulering,
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat

        val alderfradrag = command.brukereMedEpsAlder.map { brukerMedEps ->
            val eksterneFradragBruker = alderPerioder.singleOrNull { Fnr(it.fnr) == brukerMedEps.fnr }
                ?: throw IllegalStateException("Noe gikk galt fiks bedre feilmelding")

            val eskterneFradragEps = alderPerioder.filter { brukerMedEps.eps.contains(Fnr(it.fnr)) }

            RegulerteFradragEksternKilde(
                bruker = NyttFradragEksternKilde(
                    fnr = brukerMedEps.fnr,
                    førRegulering = eksterneFradragBruker.perioder[0].netto,
                    etterRegulering = eksterneFradragBruker.perioder[1].netto,
                ),
                forEps = eskterneFradragEps.map { eksterntFradragEps ->
                    NyttFradragEksternKilde(
                        fnr = Fnr(eksterntFradragEps.fnr),
                        førRegulering = eksterntFradragEps.perioder[0].netto,
                        etterRegulering = eksterntFradragEps.perioder[1].netto,
                    )
                },
            )
        }

        return SakerMedRegulerteFradragEksternKilde(uførefradrag + alderfradrag)
    }
}
