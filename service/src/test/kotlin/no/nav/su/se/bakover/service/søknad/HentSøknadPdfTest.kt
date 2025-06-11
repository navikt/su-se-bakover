package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.client.KunneIkkeGenererePdf
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.Person
import økonomi.domain.utbetaling.Utbetalinger
import java.nio.charset.StandardCharsets
import java.time.Year
import java.util.UUID

class HentSøknadPdfTest {

    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()
    private val søknadInnhold: SøknadsinnholdUføre = søknadinnholdUføre()
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
        utbetalinger = Utbetalinger(),
        type = Sakstype.UFØRE,
        versjon = Hendelsesversjon(1),
        uteståendeKravgrunnlag = null,
    )
    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "1234"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        fødsel = Person.Fødsel.MedFødselsår(
            år = Year.of(1956),
        ),
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
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn KunneIkkeGenererePdf.left()
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
                            sakstype = Sakstype.UFØRE,
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
        val pdf = PdfA("".toByteArray(StandardCharsets.UTF_8))

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
                            sakstype = Sakstype.UFØRE,
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
