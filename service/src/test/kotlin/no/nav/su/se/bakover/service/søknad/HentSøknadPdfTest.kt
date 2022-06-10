package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.nio.charset.StandardCharsets
import java.util.UUID

class HentSøknadPdfTest {

    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()
    private val søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build()
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
        utbetalinger = emptyList(),
        type = Sakstype.UFØRE,
    )
    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "1234")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
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
            personService = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
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
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
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
            personService = personServiceMock,
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe KunneIkkeLageSøknadPdf.KunneIkkeLagePdf.left()

        inOrder(søknadRepoMock, sakServiceMock, personServiceMock, pdfGeneratorMock) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe søknad.søknadInnhold.personopplysninger.fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = søknad.id,
                        navn = person.navn,
                        søknadOpprettet = søknad.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
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
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
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
            personService = personServiceMock,
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe pdf.right()
        inOrder(søknadRepoMock, sakServiceMock, personServiceMock, pdfGeneratorMock) {

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe søknad.søknadInnhold.personopplysninger.fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = søknad.id,
                        navn = person.navn,
                        søknadOpprettet = søknad.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
                    )
                }
            )
        }
        verifyNoMoreInteractions(søknadRepoMock, pdfGeneratorMock)
    }
}
