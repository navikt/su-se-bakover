package no.nav.su.se.bakover.service.regulering

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumResponseDto
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.AapVedtakStatus
import no.nav.su.se.bakover.domain.regulering.BeregnAap
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.Kildesystem
import no.nav.su.se.bakover.domain.regulering.MaksimumPeriodeDto
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.LocalDate

class AapReguleringerServiceImplTest {

    @Test
    fun `velger riktig vedtak for måneden før og på reguleringstidspunktet`() {
        val fnr = Fnr("12345678910")
        val aprilVedtak = maksimumVedtak(dagsats = 500, fraOgMed = "2026-04-01", tilOgMed = "2026-04-30")
        val maiVedtak = maksimumVedtak(
            dagsats = 525,
            barnetillegg = 36,
            fraOgMed = "2026-05-01",
            tilOgMed = "2026-05-31",
            vedtaksdato = "2026-06-01",
        )
        val service = lagService(
            vedtak = listOf(
                aprilVedtak,
                maiVedtak,
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.shouldHaveSize(1)
        resultat.beløpBruker.single().førRegulering shouldBe aprilVedtak.forventetMånedsbeløp()
        resultat.beløpBruker.single().etterRegulering shouldBe maiVedtak.forventetMånedsbeløp()
    }

    @Test
    fun `bruker eneste vedtak som er gyldig på reguleringstidspunktet når flere perioder finnes`() {
        val fnr = Fnr("12345678910")
        val maiVedtak =
            maksimumVedtak(dagsats = 525, fraOgMed = "2026-05-01", tilOgMed = "2026-05-31", vedtaksdato = "2026-06-01")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2026-04-01", tilOgMed = "2026-04-30"),
                maiVedtak,
                maksimumVedtak(dagsats = 550, fraOgMed = "2026-06-01", tilOgMed = "2026-06-30"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.shouldHaveSize(1)
        resultat.beløpBruker.single().etterRegulering shouldBe maiVedtak.forventetMånedsbeløp()
    }

    @Test
    fun `flere overlappende vedtak på reguleringstidspunktet gir eksplisitt AAP-feil`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 500, fraOgMed = "2026-04-01", tilOgMed = "2026-04-30"),
                maksimumVedtak(dagsats = 525, fraOgMed = "2026-05-01", tilOgMed = "2026-05-31"),
                maksimumVedtak(dagsats = 530, fraOgMed = "2026-05-01", tilOgMed = "2026-05-31"),
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
                maksimumVedtak(dagsats = 500, fraOgMed = "2026-04-01", tilOgMed = "2026-04-30"),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil.forEach {
            it is FeilMedEksternRegulering.IngenGyldigAapPeriode
        }
    }

    @Test
    fun `likt beløp i april og mai gir eksplisitt AAP-feil`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 650, fraOgMed = "2026-04-01", tilOgMed = "2026-04-30"),
                maksimumVedtak(
                    dagsats = 650,
                    fraOgMed = "2026-05-01",
                    tilOgMed = "2026-05-31",
                    vedtaksdato = "2026-06-01",
                ),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.AapIkkeBekreftetRegulert)
    }

    @Test
    fun `arena-stans med iverk regnes ikke som gyldig AAP-vedtak`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(
                    dagsats = 500,
                    fraOgMed = "2026-04-01",
                    tilOgMed = "2026-04-30",
                    status = AapVedtakStatus.IVERK,
                    kildesystem = Kildesystem.ARENA,
                    vedtaksTypeKode = "S",
                ),
                maksimumVedtak(
                    dagsats = 525,
                    fraOgMed = "2026-05-01",
                    tilOgMed = "2026-05-31",
                    vedtaksdato = "2026-06-01",
                    status = AapVedtakStatus.LØPENDE,
                    kildesystem = Kildesystem.KELVIN,
                ),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil.forEach {
            it is FeilMedEksternRegulering.IngenGyldigAapPeriode
        }
    }

    @Test
    fun `Ved negativ aap utvikling er det ikke regulering tror vi`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(dagsats = 650, fraOgMed = "2026-04-01", tilOgMed = "2026-04-30"),
                maksimumVedtak(
                    dagsats = 640,
                    fraOgMed = "2026-05-01",
                    tilOgMed = "2026-05-31",
                    vedtaksdato = "2026-06-01",
                ),

            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.AapBeløpErIkkeØkning)
    }

    @Test
    fun `vedtak som dekker reguleringsmåneden er ikke løpende gir manuell behandling`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(
                    dagsats = 1022,
                    fraOgMed = "2026-04-01",
                    tilOgMed = "2026-04-30",
                    status = AapVedtakStatus.IVERK,
                    kildesystem = Kildesystem.ARENA,
                ),
                // Reguleringsvedtaket er erstattet av et nyere vedtak, og er derfor AVSLU -> kan ikke kjøres automatisk
                maksimumVedtak(
                    dagsats = 1072,
                    fraOgMed = "2026-05-01",
                    tilOgMed = "2026-05-05",
                    vedtaksdato = "2026-06-01",
                    status = AapVedtakStatus.AVSLU,
                    kildesystem = Kildesystem.ARENA,
                ),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil.single()
            .shouldBeInstanceOf<FeilMedEksternRegulering.AapVedtakEtterReguleringErIkkeLøpende>()
    }

    @Test
    fun `arena base-case - foer AVSLU i april og loepende IVERK paa reguleringsmaaneden gir automatisk regulering`() {
        val fnr = Fnr("12345678910")
        val maiVedtak = maksimumVedtak(
            dagsats = 1072,
            fraOgMed = "2026-05-01",
            tilOgMed = "2026-12-06",
            vedtaksdato = "2026-05-01",
            status = AapVedtakStatus.IVERK,
            kildesystem = Kildesystem.ARENA,
        )
        val service = lagService(
            vedtak = listOf(
                // historiske vedtak er AVSLU, men brukes fortsatt som før-vedtak
                maksimumVedtak(
                    dagsats = 1022,
                    fraOgMed = "2025-12-05",
                    tilOgMed = "2026-04-30",
                    status = AapVedtakStatus.AVSLU,
                    kildesystem = Kildesystem.ARENA,
                ),
                maiVedtak,
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.shouldHaveSize(1)
        resultat.beløpBruker.single().etterRegulering shouldBe maiVedtak.forventetMånedsbeløp()
    }

    @Test
    fun `arena - IVERK paa reguleringsmaaneden med senere IVERK-forlengelse gir automatisk regulering`() {
        val fnr = Fnr("12345678910")
        val maiVedtak = maksimumVedtak(
            dagsats = 1072,
            fraOgMed = "2026-05-01",
            tilOgMed = "2026-06-15",
            vedtaksdato = "2026-05-01",
            status = AapVedtakStatus.IVERK,
            kildesystem = Kildesystem.ARENA,
        )
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(
                    dagsats = 1022,
                    fraOgMed = "2026-01-01",
                    tilOgMed = "2026-04-30",
                    status = AapVedtakStatus.AVSLU,
                    kildesystem = Kildesystem.ARENA,
                ),
                maiVedtak,
                // forlengelse etter reguleringsmåneden, dekker ikke 01.05 og påvirker ikke valget
                maksimumVedtak(
                    dagsats = 1072,
                    fraOgMed = "2026-06-16",
                    tilOgMed = "2026-12-13",
                    status = AapVedtakStatus.IVERK,
                    kildesystem = Kildesystem.ARENA,
                ),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.single().etterRegulering shouldBe maiVedtak.forventetMånedsbeløp()
    }

    @Test
    fun `arena - gammel aapen stans som overlapper paavirker ikke automatisk regulering`() {
        val fnr = Fnr("12345678910")
        val maiVedtak = maksimumVedtak(
            dagsats = 1072,
            fraOgMed = "2026-05-01",
            tilOgMed = "2026-09-25",
            vedtaksdato = "2026-05-01",
            status = AapVedtakStatus.IVERK,
            kildesystem = Kildesystem.ARENA,
        )
        val service = lagService(
            vedtak = listOf(
                // gammel åpen stans (type S) som overlapper alt - skal ignoreres
                maksimumVedtak(
                    dagsats = 912,
                    fraOgMed = "2024-04-04",
                    tilOgMed = null,
                    status = AapVedtakStatus.IVERK,
                    kildesystem = Kildesystem.ARENA,
                    vedtaksTypeKode = "S",
                ),
                maksimumVedtak(
                    dagsats = 1022,
                    fraOgMed = "2025-09-26",
                    tilOgMed = "2026-04-30",
                    status = AapVedtakStatus.AVSLU,
                    kildesystem = Kildesystem.ARENA,
                ),
                maiVedtak,
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeRight()

        resultat.beløpBruker.single().etterRegulering shouldBe maiVedtak.forventetMånedsbeløp()
    }

    @Test
    fun `arena - AVSLU stub paa reguleringsmaaneden med senere IVERK og overlappende stans gir manuell behandling`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(
                    dagsats = 1022,
                    fraOgMed = "2026-01-01",
                    tilOgMed = "2026-04-30",
                    status = AapVedtakStatus.AVSLU,
                    kildesystem = Kildesystem.ARENA,
                ),
                // vedtaket som dekker 01.05 er AVSLU (erstattet) -> manuell
                maksimumVedtak(
                    dagsats = 1072,
                    fraOgMed = "2026-05-01",
                    tilOgMed = "2026-05-16",
                    vedtaksdato = "2026-05-01",
                    status = AapVedtakStatus.AVSLU,
                    kildesystem = Kildesystem.ARENA,
                ),
                maksimumVedtak(
                    dagsats = 1072,
                    fraOgMed = "2026-05-17",
                    tilOgMed = "2027-05-16",
                    vedtaksdato = "2026-05-17",
                    status = AapVedtakStatus.IVERK,
                    kildesystem = Kildesystem.ARENA,
                    vedtaksTypeKode = "O",
                ),
                maksimumVedtak(
                    dagsats = 1022,
                    fraOgMed = "2026-05-17",
                    tilOgMed = null,
                    vedtaksdato = "2026-05-17",
                    status = AapVedtakStatus.IVERK,
                    kildesystem = Kildesystem.ARENA,
                    vedtaksTypeKode = "S",
                ),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil.single()
            .shouldBeInstanceOf<FeilMedEksternRegulering.AapVedtakEtterReguleringErIkkeLøpende>()
    }

    @Test
    fun `arena - kun aapen stans uten ordinaert vedtak gir ingen gyldig aap-periode`() {
        val fnr = Fnr("12345678910")
        val service = lagService(
            vedtak = listOf(
                maksimumVedtak(
                    dagsats = 1022,
                    fraOgMed = "2025-12-15",
                    tilOgMed = null,
                    status = AapVedtakStatus.IVERK,
                    kildesystem = Kildesystem.ARENA,
                    vedtaksTypeKode = "S",
                ),
            ),
        )

        val resultat = service.hentReguleringer(parameter(fnr = fnr)).single().shouldBeLeft()

        resultat.alleFeil.single()
            .shouldBeInstanceOf<FeilMedEksternRegulering.IngenGyldigAapPeriode>()
    }

    @Test
    fun `klientfeil gir eksplisitt AAP-feil`() {
        val client = mock<AapApiInternClient> {
            on { hentMaksimumUtenUtbetaling(any(), any(), any()) } doReturn ClientError(
                httpStatus = 500,
                message = "boom",
            ).left()
        }
        val service = AapReguleringerServiceImpl(client)

        val resultat = service.hentReguleringer(parameter(fnr = Fnr("12345678910"))).single().shouldBeLeft()

        resultat.alleFeil shouldBe listOf(FeilMedEksternRegulering.KunneIkkeHenteAap)
    }

    private fun lagService(vedtak: List<MaksimumVedtakDto>): AapReguleringerServiceImpl {
        val client = mock<AapApiInternClient> {
            on { hentMaksimumUtenUtbetaling(any(), any(), any()) } doReturn MaksimumResponseDto(vedtak).right()
        }
        return AapReguleringerServiceImpl(client)
    }

    private fun parameter(fnr: Fnr, månedFørRegulering: LocalDate? = null) = HentReguleringerPesysParameter(
        månedFørRegulering = månedFørRegulering ?: LocalDate.parse("2026-04-01"),
        brukereMedEps = listOf(
            HentReguleringerPesysParameter.BrukerMedEps(
                fnr = fnr,
                sakstype = Sakstype.UFØRE,
                fradragstyperBruker = setOf(Fradragstype.Arbeidsavklaringspenger),
                eps = null,
                fradragstyperEps = emptySet(),
                saksnummer = Saksnummer(2024L),
            ),
        ),
    )

    private fun maksimumVedtak(
        dagsats: Int,
        fraOgMed: String,
        tilOgMed: String?,
        vedtaksdato: String = fraOgMed,
        barnetillegg: Int = 0,
        status: AapVedtakStatus = AapVedtakStatus.LØPENDE,
        kildesystem: Kildesystem = Kildesystem.KELVIN,
        vedtaksTypeKode: String? = null,
    ) = MaksimumVedtakDto(
        dagsats = dagsats,
        vedtaksdato = LocalDate.parse(vedtaksdato),
        periode = MaksimumPeriodeDto(
            fraOgMedDato = LocalDate.parse(fraOgMed),
            tilOgMedDato = tilOgMed?.let { LocalDate.parse(it) },
        ),
        barnetillegg = barnetillegg,
        status = status,
        kildesystem = kildesystem,
        vedtaksTypeKode = vedtaksTypeKode,
    )

    private fun MaksimumVedtakDto.forventetMånedsbeløp(): BigDecimal =
        BeregnAap.AapBeregning.fraMaksimumVedtak(this).sats
}
