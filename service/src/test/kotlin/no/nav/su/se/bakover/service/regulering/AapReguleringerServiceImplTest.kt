package no.nav.su.se.bakover.service.regulering

import arrow.core.right
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumPeriodeDto
import no.nav.su.se.bakover.client.aap.MaksimumResponseDto
import no.nav.su.se.bakover.client.aap.MaksimumVedtakDto
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single()

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

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single()

        resultat.beløpBruker.shouldHaveSize(1)
        resultat.beløpBruker.single().etterRegulering shouldBe BigDecimal("11375.00")
    }

    @Test
    fun `flere overlappende vedtak på reguleringstidspunktet gir tomt AAP-beløp slik at saken går manuelt`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 525, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
                maksimumVedtak(dagsats = 530, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single()

        resultat.beløpBruker shouldHaveSize 0
    }

    @Test
    fun `ingen gyldig periode på reguleringstidspunktet gir tomt AAP-beløp slik at saken går manuelt`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single()

        resultat.beløpBruker shouldHaveSize 0
    }

    @Test
    fun `barnetillegg dobbelttelles ikke når dagsats allerede er totalbeløpet`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 650, fraOgMed = "2025-04-01", tilOgMed = "2025-04-30"),
                maksimumVedtak(dagsats = 650, fraOgMed = "2025-05-01", tilOgMed = "2025-05-31"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single()

        resultat.beløpBruker.single().førRegulering shouldBe BigDecimal("14083.33")
    }

    private fun lagService(vedtak: List<MaksimumVedtakDto>): AapReguleringerServiceImpl {
        val client = mock<AapApiInternClient> {
            on { hentMaksimum(any(), any(), any()) } doReturn MaksimumResponseDto(vedtak).right()
        }
        return AapReguleringerServiceImpl(client)
    }

    private fun parameter(fnr: Fnr) = HentReguleringerPesysParameter(
        månedFørRegulering = LocalDate.parse("2025-04-01"),
        brukereMedEps = listOf(
            HentReguleringerPesysParameter.BrukerMedEps(
                fnr = fnr,
                sakstype = Sakstype.UFØRE,
                fradragBruker = null,
                harAapBruker = true,
                eps = null,
                fradragEps = null,
                harAapEps = false,
            ),
        ),
    )

    private fun maksimumVedtak(
        dagsats: Int,
        fraOgMed: String,
        tilOgMed: String,
    ) = MaksimumVedtakDto(
        dagsats = dagsats,
        periode = MaksimumPeriodeDto(
            fraOgMedDato = LocalDate.parse(fraOgMed),
            tilOgMedDato = LocalDate.parse(tilOgMed),
        ),
    )
}
