package no.nav.su.se.bakover.service.regulering

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.ResponseDtoAlder
import no.nav.su.se.bakover.client.pesys.ResponseDtoUføre
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.LocalDate

class ReguleringerFraPesysServiceImplTest {

    @Test
    fun `flere pesys fradragstyper for samme person gir eksplisitt feil`() {
        val service = ReguleringerFraPesysServiceImpl(
            pesysClient = mock<PesysClient> {
                on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(emptyList(), emptyList()).right()
                on { hentVedtakForPersonPaaDatoUføre(any(), any()) } doReturn ResponseDtoUføre(emptyList(), emptyList()).right()
            },
            satsFactory = mock<SatsFactory>(),
        )

        val resultat = service.hentReguleringer(
            HentReguleringerPesysParameter(
                månedFørRegulering = LocalDate.parse("2025-04-01"),
                brukereMedEps = listOf(
                    HentReguleringerPesysParameter.BrukerMedEps(
                        fnr = Fnr("12345678910"),
                        sakstype = Sakstype.UFØRE,
                        fradragstyperBruker = setOf(Fradragstype.Uføretrygd, Fradragstype.Alderspensjon),
                        eps = null,
                        fradragstyperEps = emptySet(),
                    ),
                ),
            ),
        ).single()

        resultat.leftOrNull()?.alleFeil shouldBe listOf(FeilMedEksternRegulering.FlerePesysFradragstyperForSammePerson)
    }

    @Test
    fun `feilende fnr fra pesys alder gir feil for bruker og slipper gjennom andre brukere`() {
        val feilendeFnr = Fnr("12345678910")
        val okFnr = Fnr("12345678911")
        val månedFørRegulering = LocalDate.parse("2025-04-01")
        val service = ReguleringerFraPesysServiceImpl(
            pesysClient = mock<PesysClient> {
                on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(
                    resultat = listOf(
                        AlderBeregningsperioderPerPerson(
                            fnr = okFnr.toString(),
                            perioder = listOf(
                                AlderBeregningsperiode(
                                    netto = 1000,
                                    fom = LocalDate.parse("2025-04-01"),
                                    tom = LocalDate.parse("2025-04-30"),
                                    grunnbelop = 124028,
                                ),
                                AlderBeregningsperiode(
                                    netto = 1100,
                                    fom = LocalDate.parse("2025-05-01"),
                                    tom = null,
                                    grunnbelop = 130160,
                                ),
                            ),
                        ),
                    ),
                    feilendeFnr = listOf(feilendeFnr.toString()),
                ).right()
                on { hentVedtakForPersonPaaDatoUføre(any(), any()) } doReturn ResponseDtoUføre(emptyList(), emptyList()).right()
            },
            satsFactory = satsFactoryTestPåDato(LocalDate.parse("2025-05-23")),
        )

        val resultater = service.hentReguleringer(
            HentReguleringerPesysParameter(
                månedFørRegulering = månedFørRegulering,
                brukereMedEps = listOf(
                    HentReguleringerPesysParameter.BrukerMedEps(
                        fnr = feilendeFnr,
                        sakstype = Sakstype.ALDER,
                        fradragstyperBruker = setOf(Fradragstype.Alderspensjon),
                        eps = null,
                        fradragstyperEps = emptySet(),
                    ),
                    HentReguleringerPesysParameter.BrukerMedEps(
                        fnr = okFnr,
                        sakstype = Sakstype.ALDER,
                        fradragstyperBruker = setOf(Fradragstype.Alderspensjon),
                        eps = null,
                        fradragstyperEps = emptySet(),
                    ),
                ),
            ),
        )

        resultater.shouldHaveSize(2)
        val feil = resultater[0].shouldBeLeft()
        feil.fnr shouldBe feilendeFnr
        feil.alleFeil shouldBe listOf(
            FeilMedEksternRegulering.KunneIkkeHenteFraPesys,
            FeilMedEksternRegulering.IngenPeriodeFraPesys,
        )

        val ok = resultater[1].shouldBeRight()
        ok.brukerFnr shouldBe okFnr
        ok.beløpBruker.single().førRegulering shouldBe BigDecimal.valueOf(1000).setScale(2)
        ok.beløpBruker.single().etterRegulering shouldBe BigDecimal.valueOf(1100).setScale(2)
    }

    @Test
    fun `feilende fnr fra pesys uføre gir feil for bruker og slipper gjennom andre brukere`() {
        val feilendeFnr = Fnr("12345678910")
        val okFnr = Fnr("12345678911")
        val månedFørRegulering = LocalDate.parse("2025-04-01")
        val service = ReguleringerFraPesysServiceImpl(
            pesysClient = mock<PesysClient> {
                on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(
                    resultat = emptyList(),
                    feilendeFnr = emptyList(),
                ).right()
                on { hentVedtakForPersonPaaDatoUføre(any(), any()) } doReturn ResponseDtoUføre(
                    resultat = listOf(
                        UføreBeregningsperioderPerPerson(
                            fnr = okFnr.toString(),
                            perioder = listOf(
                                UføreBeregningsperiode(
                                    netto = 1000,
                                    fom = LocalDate.parse("2025-04-01"),
                                    tom = LocalDate.parse("2025-04-30"),
                                    grunnbelop = 124028,
                                    oppjustertInntektEtterUfore = 100,
                                ),
                                UføreBeregningsperiode(
                                    netto = 1100,
                                    fom = LocalDate.parse("2025-05-01"),
                                    tom = null,
                                    grunnbelop = 130160,
                                    oppjustertInntektEtterUfore = 110,
                                ),
                            ),
                        ),
                    ),
                    feilendeFnr = listOf(feilendeFnr.toString()),
                ).right()
            },
            satsFactory = satsFactoryTestPåDato(LocalDate.parse("2025-05-23")),
        )

        val resultater = service.hentReguleringer(
            HentReguleringerPesysParameter(
                månedFørRegulering = månedFørRegulering,
                brukereMedEps = listOf(
                    HentReguleringerPesysParameter.BrukerMedEps(
                        fnr = feilendeFnr,
                        sakstype = Sakstype.UFØRE,
                        fradragstyperBruker = setOf(Fradragstype.Uføretrygd),
                        eps = null,
                        fradragstyperEps = emptySet(),
                    ),
                    HentReguleringerPesysParameter.BrukerMedEps(
                        fnr = okFnr,
                        sakstype = Sakstype.UFØRE,
                        fradragstyperBruker = setOf(Fradragstype.Uføretrygd),
                        eps = null,
                        fradragstyperEps = emptySet(),
                    ),
                ),
            ),
        )

        resultater.shouldHaveSize(2)
        val feil = resultater[0].shouldBeLeft()
        feil.fnr shouldBe feilendeFnr
        feil.alleFeil shouldBe listOf(
            FeilMedEksternRegulering.KunneIkkeHenteFraPesys,
            FeilMedEksternRegulering.IngenPeriodeFraPesys,
            FeilMedEksternRegulering.IngenPeriodeFraPesys,
        )

        val ok = resultater[1].shouldBeRight()
        ok.brukerFnr shouldBe okFnr
        ok.beløpBruker.single().førRegulering shouldBe BigDecimal.valueOf(1000).setScale(2)
        ok.beløpBruker.single().etterRegulering shouldBe BigDecimal.valueOf(1100).setScale(2)
    }
}
