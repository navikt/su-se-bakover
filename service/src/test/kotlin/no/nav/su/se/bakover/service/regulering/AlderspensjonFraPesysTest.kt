package no.nav.su.se.bakover.service.regulering

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.ResponseDtoAlder
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.regulering.AlderspensjonOppslagsgrunnlag
import no.nav.su.se.bakover.domain.regulering.AlderspensjonOppslagsperson
import no.nav.su.se.bakover.domain.regulering.AlderspensjonsbeløpFraPesys
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.math.BigDecimal
import java.time.LocalDate

class AlderspensjonFraPesysTest {
    private val brukerFnr = Fnr("12345678910")
    private val epsFnr = Fnr("12345678911")
    private val dato = LocalDate.parse("2025-10-01")
    private val måned = dato.toMåned()
    private val forventetG = 130160
    private val uregulertG = 118620
    private val satsFactory = satsFactoryTestPåDato(dato)

    @Test
    fun `henter kun kjøringsmåneden for bruker og EPS uten å validere tidligere perioder`() {
        val tidligerePeriode = AlderBeregningsperiode(
            netto = 900,
            fom = dato.minusMonths(1),
            tom = dato.minusDays(1),
            grunnbelop = uregulertG,
        )
        val brukerBeløp = 1100
        val epsBeløp = 2200
        val klient = klient(
            ResponseDtoAlder(
                resultat = listOf(
                    AlderBeregningsperioderPerPerson(
                        fnr = brukerFnr.toString(),
                        perioder = listOf(tidligerePeriode, tidligerePeriode, periode(brukerBeløp)),
                    ),
                    AlderBeregningsperioderPerPerson(
                        fnr = epsFnr.toString(),
                        perioder = listOf(periode(epsBeløp)),
                    ),
                ),
                feilendeFnr = emptyList(),
            ),
        )
        val resultat = ReguleringerFraPesysServiceImpl(klient).hentReguleringerForOmregningAlder(
            måned = måned,
            oppslagsgrunnlag = listOf(grunnlag(setOf(FradragTilhører.BRUKER, FradragTilhører.EPS))),
            satsFactory = satsFactory,
        ).single().shouldBeRight()

        resultat.brukerFnr shouldBe brukerFnr
        resultat.epsFnr shouldBe epsFnr
        resultat.beløp shouldBe listOf(
            AlderspensjonsbeløpFraPesys(FradragTilhører.BRUKER, måned, BigDecimal.valueOf(brukerBeløp.toLong()).setScale(2)),
            AlderspensjonsbeløpFraPesys(FradragTilhører.EPS, måned, BigDecimal.valueOf(epsBeløp.toLong()).setScale(2)),
        )
        verify(klient).hentVedtakForPersonPaaDatoAlder(listOf(brukerFnr, epsFnr), dato)
        verifyNoMoreInteractions(klient)
    }

    @Test
    fun `ulik G i kjøringsmåneden feiler med tilhørighet og begge grunnbeløp`() {
        val klient = klient(
            ResponseDtoAlder(
                resultat = listOf(
                    AlderBeregningsperioderPerPerson(
                        fnr = epsFnr.toString(),
                        perioder = listOf(periode(grunnbeløp = uregulertG)),
                    ),
                ),
                feilendeFnr = emptyList(),
            ),
        )
        val feil = ReguleringerFraPesysServiceImpl(klient).hentReguleringerForOmregningAlder(
            måned = måned,
            oppslagsgrunnlag = listOf(grunnlag(setOf(FradragTilhører.EPS))),
            satsFactory = satsFactory,
        ).single().shouldBeLeft()

        val forventetFeil = FeilMedEksternRegulering.GrunnbeløpFraPesysUliktForventetNytt(forventetG, uregulertG)
        feil.feilBruker shouldBe emptyList()
        feil.feilEps shouldBe listOf(forventetFeil)
        feil.alleFeil shouldBe listOf(forventetFeil)
    }

    @Test
    fun `overlapp i kjøringsmåneden feiler`() {
        val periode = periode()
        val klient = klient(
            ResponseDtoAlder(
                resultat = listOf(
                    AlderBeregningsperioderPerPerson(brukerFnr.toString(), listOf(periode, periode)),
                ),
                feilendeFnr = emptyList(),
            ),
        )
        val feil = ReguleringerFraPesysServiceImpl(klient).hentReguleringerForOmregningAlder(
            måned = måned,
            oppslagsgrunnlag = listOf(grunnlag(setOf(FradragTilhører.BRUKER))),
            satsFactory = satsFactory,
        ).single().shouldBeLeft()

        feil.feilBruker shouldBe listOf(FeilMedEksternRegulering.OverlappendePerioderInnenforPesysPeriode)
    }

    @Test
    fun `feilende oppslag for EPS feiler selv om responsen inneholder et beløp`() {
        val klient = klient(
            ResponseDtoAlder(
                resultat = listOf(
                    AlderBeregningsperioderPerPerson(epsFnr.toString(), listOf(periode())),
                ),
                feilendeFnr = listOf(epsFnr.toString()),
            ),
        )
        val feil = ReguleringerFraPesysServiceImpl(klient).hentReguleringerForOmregningAlder(
            måned = måned,
            oppslagsgrunnlag = listOf(grunnlag(setOf(FradragTilhører.EPS))),
            satsFactory = satsFactory,
        ).single().shouldBeLeft()

        feil.feilEps shouldBe listOf(FeilMedEksternRegulering.KunneIkkeHenteFraPesys)
        feil.feilBruker shouldBe emptyList()
    }

    @Test
    fun `uten registrert norsk alderspensjonsfradrag gjøres ingen oppslag`() {
        val klient = mock<PesysClient>()
        val resultat = ReguleringerFraPesysServiceImpl(klient).hentReguleringerForOmregningAlder(
            måned = måned,
            oppslagsgrunnlag = listOf(grunnlag(emptySet())),
            satsFactory = satsFactory,
        ).single().shouldBeRight()

        resultat.beløp shouldBe emptyList()
        verifyNoMoreInteractions(klient)
    }

    private fun grunnlag(fradragFor: Set<FradragTilhører>) = AlderspensjonOppslagsgrunnlag(
        brukerFnr = brukerFnr,
        personer = fradragFor.map { tilhører ->
            AlderspensjonOppslagsperson(
                fnr = when (tilhører) {
                    FradragTilhører.BRUKER -> brukerFnr
                    FradragTilhører.EPS -> epsFnr
                },
                tilhører = tilhører,
            )
        },
    )

    private fun periode(beløp: Int = 1000, grunnbeløp: Int = forventetG) = AlderBeregningsperiode(
        netto = beløp,
        fom = dato,
        tom = null,
        grunnbelop = grunnbeløp,
    )

    private fun klient(respons: ResponseDtoAlder) = mock<PesysClient> {
        on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn respons.right()
    }
}
