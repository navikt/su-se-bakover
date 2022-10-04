package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.veileder
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
        innsendtAv = veileder,
    )

    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(2021),
        opprettet = Tidspunkt.EPOCH,
        fnr = Fnr(fnr = "12345678901"),
        søknader = listOf(søknad),
        utbetalinger = emptyList(),
        type = Sakstype.UFØRE,
        uteståendeAvkorting = Avkortingsvarsel.Ingen,
    )
    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "1234"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )

    @Test
    fun `fant ikke søknad`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknad(any()) } doReturn null
            },
        ).also {
            it.service.hentSøknadPdf(søknadId) shouldBe KunneIkkeLageSøknadPdf.FantIkkeSøknad.left()
            verify(it.søknadRepo).hentSøknad(argThat { it shouldBe søknadId })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kunne ikke generere PDF`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknad(any()) } doReturn søknad
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn ClientError(0, "").left()
            },
        ).also {
            it.service.hentSøknadPdf(søknadId) shouldBe KunneIkkeLageSøknadPdf.KunneIkkeLagePdf.left()

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknad(argThat { it shouldBe søknadId })
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPerson(argThat { it shouldBe søknad.fnr })
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = søknad.id,
                            navn = person.navn,
                            søknadOpprettet = søknad.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `henter PDF`() {
        val pdf = "".toByteArray(StandardCharsets.UTF_8)

        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknad(any()) } doReturn søknad
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
            },
        ).also {
            it.service.hentSøknadPdf(søknadId) shouldBe pdf.right()

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknad(argThat { it shouldBe søknadId })
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPerson(argThat { it shouldBe søknad.fnr })
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = søknad.id,
                            navn = person.navn,
                            søknadOpprettet = søknad.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
            }
            it.verifyNoMoreInteractions()
        }
    }
}
