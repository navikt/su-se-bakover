package no.nav.su.se.bakover.service.regulering

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumResponseDto
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.ResponseDtoAlder
import no.nav.su.se.bakover.client.pesys.ResponseDtoUføre
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.MaksimumPeriodeDto
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.LocalDate

class AapReguleringerServiceImplTest {

    @Test
    fun `velger riktig vedtak for måneden før og på reguleringstidspunktet`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 525, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.shouldHaveSize(1)
        resultat.beløpBruker.single().førRegulering shouldBe BigDecimal("10833.33")
        resultat.beløpBruker.single().etterRegulering shouldBe BigDecimal("11375.00")
    }

    @Test
    fun `bruker eneste vedtak som er gyldig på reguleringstidspunktet når flere perioder finnes`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 525, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
                maksimumVedtak(dagsats = 550, fraOgMed = "2025-06-01", tilOgMed = "2025-06-30"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.shouldHaveSize(1)
        resultat.beløpBruker.single().etterRegulering shouldBe BigDecimal("11375.00")
    }

    @Test
    fun `flere overlappende vedtak på reguleringstidspunktet gir eksplisitt AAP-feil`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 525, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
                maksimumVedtak(dagsats = 530, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.FlereGyldigeAapPerioder)
    }

    @Test
    fun `ingen gyldig periode på reguleringstidspunktet gir eksplisitt AAP-feil`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.IngenGyldigAapPeriode)
    }

    @Test
    fun `barnetillegg dobbelttelles ikke når dagsats allerede er totalbeløpet`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 650, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 675, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.single().førRegulering shouldBe BigDecimal("14083.33")
    }

    @Test
    fun `likt beløp i april og mai gir eksplisitt AAP-feil`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 650, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 650, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.AapIkkeBekreftetRegulert)
    }

    @Test
    fun `Ved negativ aap utvikling er det ikke regulering tror vi`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 650, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 640, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.AapBeløpErIkkeØkning)
    }

    @Test
    fun aapVedtaksdatoErikkeSammeSomReguleringtidspunkt() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 650, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 660, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31", vedtaksdato = "2025-06-15"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr, månedFørRegulering = LocalDate.parse("2025-04-01"))).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.AapVedtaksdatoErikkeSammeSomReguleringtidspunkt)
    }

    @Test
    fun `klientfeil gir eksplisitt AAP-feil`() {
        val client = mock<AapApiInternClient> {
            on { hentMaksimum(any(), any(), any(), any()) } doReturn ClientError(httpStatus = 500, message = "boom").left()
        }
        val service = AapReguleringerServiceImpl(client)

        val resultat = service.hentReguleringer(parameter(fnr = Fnr("12345678910"))).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.KunneIkkeHenteAap)
    }

    @Test
    fun `flere pesys fradragstyper for samme person gir eksplisitt feil`() {
        val service = ReguleringerFraPesysServiceImpl(
            pesysClient = mock<PesysClient> {
                on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(emptyList()).right()
                on { hentVedtakForPersonPaaDatoUføre(any(), any()) } doReturn ResponseDtoUføre(emptyList()).right()
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

    private fun lagService(vedtak: List<MaksimumVedtakDto>): AapReguleringerServiceImpl {
        val client = mock<AapApiInternClient> {
            on { hentMaksimum(any(), any(), any(), any()) } doReturn MaksimumResponseDto(vedtak).right()
        }
        return AapReguleringerServiceImpl(client)
    }

    private fun parameter(fnr: Fnr, månedFørRegulering: LocalDate? = null) = HentReguleringerPesysParameter(
        månedFørRegulering = månedFørRegulering ?: LocalDate.parse("2025-04-01"),
        brukereMedEps = listOf(
            HentReguleringerPesysParameter.BrukerMedEps(
                fnr = fnr,
                sakstype = Sakstype.UFØRE,
                fradragstyperBruker = setOf(Fradragstype.Arbeidsavklaringspenger),
                eps = null,
                fradragstyperEps = emptySet(),
            ),
        ),
    )

    private fun maksimumVedtak(
        dagsats: Int,
        fraOgMed: String,
        tilOgMed: String,
        vedtaksdato: String = fraOgMed,
    ) = MaksimumVedtakDto(
        dagsats = dagsats,
        vedtaksdato = LocalDate.parse(vedtaksdato),
        periode = MaksimumPeriodeDto(
            fraOgMedDato = LocalDate.parse(fraOgMed),
            tilOgMedDato = LocalDate.parse(tilOgMed),
        ),
    )
}
