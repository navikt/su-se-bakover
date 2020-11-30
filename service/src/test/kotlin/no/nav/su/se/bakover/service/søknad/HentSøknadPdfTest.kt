package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class HentSøknadPdfTest {

    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()
    private val søknadInnhold: SøknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val søknad = Søknad.Ny(
        id = søknadId,
        opprettet = Tidspunkt.EPOCH,
        sakId = sakId,
        søknadInnhold = søknadInnhold,
    )

    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(2021),
        opprettet = Tidspunkt.EPOCH,
        fnr = Fnr(fnr = "12345678901"),
        søknader = listOf(søknad),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = listOf()
        )
    )
    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "1234")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        telefonnummer = null,
        adresse = listOf(),
        statsborgerskap = null,
        kjønn = null,
        adressebeskyttelse = null,
        skjermet = null,
        kontaktinfo = null,
        vergemål = null,
        fullmakt = null
    )

    @Test
    fun `fant ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }

        val pdfGeneratorMock = mock<PdfGenerator>()
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = mock(),
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            personOppslag = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe KunneIkkeLageSøknadPdf.FantIkkeSøknad.left()
        verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
        verifyNoMoreInteractions(søknadRepoMock, pdfGeneratorMock)
    }

    @Test
    fun `kunne ikke generere PDF`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknad
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn ClientError(0, "").left()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            personOppslag = personOppslagMock,
            oppgaveService = mock(),
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe KunneIkkeLageSøknadPdf.KunneIkkeLagePdf.left()

        inOrder(søknadRepoMock, sakServiceMock, personOppslagMock, pdfGeneratorMock) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personOppslagMock).person(argThat { it shouldBe søknad.søknadInnhold.personopplysninger.fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold(
                        saksnummer = sak.saksnummer,
                        søknadsId = søknad.id,
                        navn = person.navn,
                        søknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
                        søknadInnhold = søknadInnhold
                    )
                }
            )
        }
        verifyNoMoreInteractions(søknadRepoMock, pdfGeneratorMock)
    }

    @Test
    fun `henter PDF`() {
        val pdf = "".toByteArray(StandardCharsets.UTF_8)

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknad
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            personOppslag = personOppslagMock,
            oppgaveService = mock(),
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe pdf.right()
        inOrder(søknadRepoMock, sakServiceMock, personOppslagMock, pdfGeneratorMock) {

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personOppslagMock).person(argThat { it shouldBe søknad.søknadInnhold.personopplysninger.fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold(
                        saksnummer = sak.saksnummer,
                        søknadsId = søknad.id,
                        navn = person.navn,
                        søknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
                        søknadInnhold = søknadInnhold
                    )
                }
            )
        }
        verifyNoMoreInteractions(søknadRepoMock, pdfGeneratorMock)
    }
}
