package no.nav.su.se.bakover.service.brev

import arrow.core.getOrElse
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.LukketSøknadPdfTemplate
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.LocalDate
import java.util.UUID

internal class BrevServiceImplTest {
    private val sakId = UUID.randomUUID()
    private val pdf = PdfGeneratorStub.pdf.toByteArray()
    private val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.now(),
        fnr = Fnr("12345678901"),
        søknader = mutableListOf(),
        behandlinger = mutableListOf(),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            utbetalinger = emptyList()
        )
    )
    private val søknad = Søknad(
        sakId = sakId,
        id = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build()
    )
    private val lukketSøknadBody = Søknad.LukketSøknadBody(
        datoSøkerTrakkSøknad = LocalDate.now(),
        typeLukking = Søknad.TypeLukking.Trukket
    )

    @Test
    fun `journalfører en lukket søknad, og sender brev`() {
        val person = PersonOppslagStub.person(sak.fnr).getOrElse {
            throw Exception("Fikk ikke person")
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = sakId) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on {
                it.person(sak.fnr)
            } doReturn person.right()
        }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on {
                genererPdf(
                    lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                        person = person,
                        søknad = søknad,
                        lukketSøknadBody = lukketSøknadBody
                    ),
                    lukketSøknadPdfTemplate = LukketSøknadPdfTemplate.TRUKKET
                )
            } doReturn pdf.right()
        }

        val dokArkivMock = mock<DokArkiv> {
            on {
                it.opprettJournalpost(
                    Journalpost.lukketSøknadJournalpostRequest(
                        person = person,
                        pdf = pdf,
                        sakId = sakId,
                        lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                            person = person,
                            søknad = søknad,
                            lukketSøknadBody = lukketSøknadBody
                        )
                    )
                )
            } doReturn "en journalpost id".right()
        }

        val dokdistFordelingMock = mock<DokDistFordeling> {
            on {
                it.bestillDistribusjon("en journalpost id")
            } doReturn "en bestillings id".right()
        }

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personOppslagMock,
            dokArkiv = dokArkivMock,
            dokDistFordeling = dokdistFordelingMock,
            sakService = sakServiceMock
        ).journalførLukketSøknadOgSendBrev(sakId, søknad, lukketSøknadBody) shouldBe "en bestillings id".right()

        verify(sakServiceMock, Times(1)).hentSak(
            argThat<UUID> { it shouldBe sakId }
        )
        verify(personOppslagMock, Times(1)).person(
            argThat { it shouldBe sak.fnr },
        )
        verify(pdfGeneratorMock, Times(1)).genererPdf(
            argThat<LukketSøknadBrevinnhold> {
                it shouldBe LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                    person = person,
                    søknad = søknad,
                    lukketSøknadBody = lukketSøknadBody
                )
            },
            argThat { it shouldBe LukketSøknadPdfTemplate.TRUKKET }
        )
        verify(dokArkivMock, Times(1)).opprettJournalpost(
            argThat {
                it shouldBe Journalpost.lukketSøknadJournalpostRequest(
                    person = person,
                    pdf = pdf,
                    sakId = sakId,
                    lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                        person = person,
                        søknad = søknad,
                        lukketSøknadBody = lukketSøknadBody
                    )
                )
            }
        )

        verify(dokdistFordelingMock, Times(1)).bestillDistribusjon(
            argThat { it shouldBe "en journalpost id" }
        )
    }

    @Test
    fun `lager et brevutkast for lukket søknad`() {
        val person = PersonOppslagStub.person(sak.fnr).getOrElse {
            throw RuntimeException("Fant ikke person for fnr ${sak.fnr}")
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = sakId) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on {
                it.person(sak.fnr)
            } doReturn person.right()
        }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on {
                genererPdf(
                    lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                        person = person,
                        søknad = søknad,
                        lukketSøknadBody = lukketSøknadBody
                    ),
                    lukketSøknadPdfTemplate = LukketSøknadPdfTemplate.TRUKKET
                )
            } doReturn pdf.right()
        }
        val dokArkivMock = mock<DokArkiv> {}
        val dokdistFordelingMock = mock<DokDistFordeling> {}

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personOppslagMock,
            dokArkiv = dokArkivMock,
            dokDistFordeling = dokdistFordelingMock,
            sakService = sakServiceMock
        ).lagLukketSøknadBrevutkast(
            sakId = sakId,
            søknad = søknad,
            lukketSøknadBody = lukketSøknadBody
        ) shouldBe pdf.right()

        verify(sakServiceMock, Times(1)).hentSak(
            argThat<UUID> { it shouldBe sakId }
        )
        verify(personOppslagMock, Times(1)).person(
            argThat { it shouldBe sak.fnr },
        )
        verify(pdfGeneratorMock, Times(1)).genererPdf(
            argThat<LukketSøknadBrevinnhold> {
                it shouldBe LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                    person = person,
                    søknad = søknad,
                    lukketSøknadBody = lukketSøknadBody
                )
            },
            argThat { it shouldBe LukketSøknadPdfTemplate.TRUKKET }
        )
    }
}
