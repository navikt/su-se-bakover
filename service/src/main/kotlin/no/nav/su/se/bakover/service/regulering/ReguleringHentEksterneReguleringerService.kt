package no.nav.su.se.bakover.service.regulering

import arrow.core.getOrElse
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.RegulertFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.RegulerteFradragEksternKilde
import no.nav.su.se.bakover.service.regulering.HentEksterneReguleringerRequest.BrukerMedEps
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import kotlin.collections.flatMap
import kotlin.collections.map

class ReguleringHentEksterneReguleringerService(private val pesysClient: PesysClient) {

    private val log = LoggerFactory.getLogger(this::class.java)

    // TODO AUTO-REG-26 feilhåndtering..
    fun hentEksterneReguleringer(request: HentEksterneReguleringerRequest): List<RegulerteFradragEksternKilde> {
        val (månedFørRegulering, brukereMedEpsUføre, brukereMedEpsAlder) = request

        val uførePerioder = hentPerioderUføre(brukereMedEpsUføre.listeAlleUnikeFnr(), månedFørRegulering)
        val uførefradrag = hentReguleringerForBrukere(
            brukereMedEps = brukereMedEpsUføre,
            perioder = uførePerioder,
        )

        val alderPerioder = hentPerioderAlder(brukereMedEpsAlder.listeAlleUnikeFnr(), månedFørRegulering)
        val alderfradrag = hentReguleringerForBrukere(
            brukereMedEps = brukereMedEpsAlder,
            perioder = alderPerioder,
        )

        return uførefradrag + alderfradrag
    }

    private fun hentReguleringerForBrukere(
        brukereMedEps: List<BrukerMedEps>,
        perioder: List<PesysPerioderForPerson>,
    ): List<RegulerteFradragEksternKilde> {
        return brukereMedEps.map { brukerMedEps ->
            val eksterneFradragBruker = perioder.hentForventedePesysPerioder(brukerMedEps.fnr)
            val eksterneFradragEps = brukerMedEps.eps.map { perioder.hentForventedePesysPerioder(it) }
            RegulerteFradragEksternKilde(
                bruker = fraPesysPeriodeTilFradrag(eksterneFradragBruker),
                forEps = eksterneFradragEps.map { fraPesysPeriodeTilFradrag(it) },
            )
        }
    }

    private fun List<PesysPerioderForPerson>.hentForventedePesysPerioder(fnr: Fnr): PesysPerioderForPerson {
        val forventetPesysPerioder = this.singleOrNull { Fnr(it.fnr) == fnr }
        if (forventetPesysPerioder == null) {
            log.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Se sikkerlogg for detaljer.")
            sikkerLogg.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Bruker=$fnr")
            throw IngenPesysPerioderFunnet()
        }
        return forventetPesysPerioder
    }

    private fun fraPesysPeriodeTilFradrag(alderForPerson: PesysPerioderForPerson): RegulertFradragEksternKilde {
        // TODO AUTO-REG-26 valider at stemmer med forventet G
        // TODO AUTO-REG-26 ta i bruk inntekt etter uføre hvis uføretrygd
        return RegulertFradragEksternKilde(
            fnr = Fnr(alderForPerson.fnr),
            førRegulering = alderForPerson.perioder[0].netto,
            etterRegulering = alderForPerson.perioder[1].netto,
        )
    }

    private fun hentPerioderUføre(fnrList: List<Fnr>, dato: LocalDate) =
        pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = fnrList,
            dato = dato,
        ).getOrElse {
            // TODO AUTO-REG-26 feilhåndtering
            throw Exception("")
        }.resultat

    private fun hentPerioderAlder(fnrList: List<Fnr>, dato: LocalDate) =
        pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = fnrList,
            dato = dato,
        ).getOrElse {
            // TODO AUTO-REG-26 feilhåndtering
            throw Exception("")
        }.resultat
}

data class HentEksterneReguleringerRequest(
    val månedFørRegulering: LocalDate,
    val brukereMedEpsUføre: List<BrukerMedEps>,
    val brukereMedEpsAlder: List<BrukerMedEps>,
) {
    data class BrukerMedEps(
        val fnr: Fnr,
        val eps: List<Fnr>,
    )

    companion object {
        fun toRequest(
            reguleringsMåned: Måned,
            forSaker: List<Sak>,
            clock: Clock,
        ): HentEksterneReguleringerRequest {
            val (uføreSaker, alderSaker) = forSaker.partition { it.type == Sakstype.UFØRE }
            return HentEksterneReguleringerRequest(
                månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1),
                brukereMedEpsUføre = uføreSaker.map { it.toBrukerMedEps(reguleringsMåned, clock) },
                brukereMedEpsAlder = alderSaker.map { it.toBrukerMedEps(reguleringsMåned, clock) },
            )
        }
        private fun Sak.toBrukerMedEps(
            reguleringsMåned: Måned,
            clock: Clock,
        ) = BrukerMedEps(
            fnr = fnr,
            eps = hentGjeldendeVedtaksdata(reguleringsMåned, clock).getOrNull()?.grunnlagsdata?.eps
                ?: emptyList(),
        )
    }
}

private fun List<BrukerMedEps>.listeAlleUnikeFnr(): List<Fnr> = this.flatMap { listOf(it.fnr) + it.eps }.distinct()

class IngenPesysPerioderFunnet : IllegalStateException()
