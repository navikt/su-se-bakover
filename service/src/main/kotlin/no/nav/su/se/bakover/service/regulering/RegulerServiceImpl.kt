package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate

class RegulerServiceImpl(
    private val vedtakService: VedtakService,
    private val sakService: SakService,
    private val clock: Clock,

) : ReguleringService {

    override fun hentAlleSakerSomKanReguleres(fraDato: LocalDate): Either<KanIkkeHenteSaker, SakerSomKanReguleres> {
        val alleSaker = hentAlleSaker(fraDato)

        val automatisk = finnAlleSakerSomKanReguleresAutomatisk(alleSaker, fraDato)

        val manuelt = (alleSaker - automatisk).map {
            SakSomKanReguleres(
                sakId = it.id,
                saksnummer = it.saksnummer,
                begrunnelse = "",
                type = "MANUELL",
            )
        }

        return SakerSomKanReguleres(
            saker = automatisk.map {
                SakSomKanReguleres(
                    sakId = it.id,
                    saksnummer = it.saksnummer,
                    begrunnelse = "",
                    type = "AUTOMATISK",
                )
            } + manuelt,
        ).right()
    }

    // override fun hentAlleSakerSomKanReguleresAutomatisk(fraDato: LocalDate): Either<KanIkkeHenteSaker, List<Saksnummer>> {
    //
    //     return hentALleSakerSomKanReguleresAutomatisk(fraDato).map {
    //         it.saksnummer
    //     }.right()
    // }
    //
    // override fun hentAlleSakerSomKanReguleresManuelt(fraDato: LocalDate): Either<KanIkkeHenteSaker, List<Saksnummer>> {
    //     return (hentAlleSaker(fraDato) - hentALleSakerSomKanReguleresAutomatisk(fraDato)).map {
    //         it.saksnummer
    //     }.right()
    // }

    private fun finnAlleSakerSomKanReguleresAutomatisk(saker: List<Sak>, fraDato: LocalDate): List<Sak> {
        return saker.filterNot {
            it.hentGjeldendeVilkårOgGrunnlag(Periode.create(fraOgMed = fraDato, tilOgMed = LocalDate.MAX), clock).let {
                it.grunnlagsdata.fradragsgrunnlag.any {
                    it.fradragstype == Fradragstype.NAVytelserTilLivsopphold ||
                        it.fradragstype == Fradragstype.OffentligPensjon
                    // || it.fradragstype == Fradragstype.ForventetInntekt && it.månedsbeløp > 0.0  // hva gjør vi med denne?
                }
            }
        }
    }

    private fun hentAlleSaker(fraDato: LocalDate): List<Sak> {
        return vedtakService.hentAlleSakIdSomHarVedtakEtterDato(fraDato).map {
            sakService.hentSak(it).getOrHandle { throw IllegalStateException("tull") }
        }.filter {
            // skal vi fjerne stans?
            it.hentPerioderMedLøpendeYtelse().any {
                Periode.create(fraOgMed = fraDato, tilOgMed = LocalDate.MAX).overlapper(it)
            }
        }
    }
}
