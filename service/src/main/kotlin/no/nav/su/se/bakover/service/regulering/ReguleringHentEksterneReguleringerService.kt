package no.nav.su.se.bakover.service.regulering

import arrow.core.getOrElse
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.NyttFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.RegulerteFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.SakerMedRegulerteFradragEksternKilde
import java.time.Clock

class ReguleringHentEksterneReguleringerService(
    private val pesysClient: PesysClient,
    private val clock: Clock,
) {

    fun hentEksterneReguleringer(
        reguleringsMåned: Måned,
        saker: List<Sak>,
    ): SakerMedRegulerteFradragEksternKilde {
        // TODO feilhåndtering..
        val månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1)

        val sakToEpsFnr = saker.associateBy(
            keySelector = { it },
            valueTransform = {
                it.hentGjeldendeVedtaksdata(periode = reguleringsMåned, clock = clock).getOrNull()?.grunnlagsdata?.eps
                    ?: emptyList()
            },
        )

        // TODO Må resultat fra uføre og alder mappes til et fnr? Trolig ja, spesielt når vi henter aap ??

        val fnrListUføre =
            sakToEpsFnr.filter { it.key.type == Sakstype.UFØRE }.map { listOf(it.key.fnr) + it.value }.flatten()
                .distinct()
        val uførePerioder = pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = fnrListUføre,
            dato = månedFørRegulering,
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat

        val uførefradrag = sakToEpsFnr.filter { it.key.type == Sakstype.UFØRE }.map { (sak, eps) ->
            val eksterneFradragBruker = uførePerioder.singleOrNull { it.fnr == sak.fnr.toString() }
                ?: throw IllegalStateException("Noe gikk galt fiks bedre feilmelding")
            val eskterneFradragEps = uførePerioder.filter { eps.map { it.toString() }.contains(it.fnr) }
            RegulerteFradragEksternKilde(
                saksnummer = sak.saksnummer,
                forBruker = NyttFradragEksternKilde(
                    // TODO bjg mer robust logikk for dette so forsikret om kun to perioder hvor før og etter regulering
                    førRegulering = eksterneFradragBruker.perioder[0].netto,
                    etterRegulering = eksterneFradragBruker.perioder[1].netto,
                ),
                forEps = eskterneFradragEps.map { eksterntFradragEps ->
                    NyttFradragEksternKilde(
                        // TODO bjg mer robust logikk for dette so forsikret om kun to perioder hvor før og etter regulering
                        førRegulering = eksterntFradragEps.perioder[0].netto,
                        etterRegulering = eksterntFradragEps.perioder[1].netto,
                    )
                },
            )
        }

        val fnrListAlder =
            sakToEpsFnr.filter { it.key.type == Sakstype.ALDER }.map { listOf(it.key.fnr) + it.value }.flatten()
                .distinct()
        val alderPerioder = pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = fnrListAlder,
            dato = månedFørRegulering,
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat

        val alderfradrag = sakToEpsFnr.filter { it.key.type == Sakstype.ALDER }.map { (sak, eps) ->
            val eksterneFradragBruker = alderPerioder.singleOrNull { it.fnr == sak.fnr.toString() }
                ?: throw IllegalStateException("Noe gikk galt fiks bedre feilmelding")
            val eskterneFradragEps = alderPerioder.filter { eps.map { it.toString() }.contains(it.fnr) }
            RegulerteFradragEksternKilde(
                saksnummer = sak.saksnummer,
                forBruker = NyttFradragEksternKilde(
                    // TODO bjg mer robust logikk for dette so forsikret om kun to perioder hvor før og etter regulering
                    førRegulering = eksterneFradragBruker.perioder[0].netto,
                    etterRegulering = eksterneFradragBruker.perioder[1].netto,
                ),
                forEps = eskterneFradragEps.map { eksterntFradragEps ->
                    NyttFradragEksternKilde(
                        // TODO bjg mer robust logikk for dette so forsikret om kun to perioder hvor før og etter regulering
                        førRegulering = eksterntFradragEps.perioder[0].netto,
                        etterRegulering = eksterntFradragEps.perioder[1].netto,
                    )
                },
            )
        }

        return SakerMedRegulerteFradragEksternKilde(uførefradrag + alderfradrag)
    }
}
