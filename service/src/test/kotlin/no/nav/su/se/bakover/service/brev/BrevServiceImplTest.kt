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
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Telefonnummer
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class BrevServiceImplTest {
    private val sakId = UUID.randomUUID()
    private val pdf = "some-pdf-document".toByteArray()
    private val fnr = Fnr("12345678901")
    private val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
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
    private val lukketSøknad = Søknad.Lukket.Trukket(
        tidspunkt = Tidspunkt.now(),
        saksbehandler = Saksbehandler(navIdent = "12345"),
        datoSøkerTrakkSøknad = LocalDate.now()
    )

    private val person = Person(
        ident = Ident(fnr, AktørId("2437280977705")),
        navn = Person.Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy"
        ),
        telefonnummer = Telefonnummer(landskode = "47", nummer = "12345678"),
        adresse = Person.Adresse(
            adressenavn = "Oslogata",
            husnummer = "12",
            husbokstav = null,
            bruksenhet = "U1H20",
            poststed = Person.Poststed(postnummer = "0050", poststed = "OSLO"),
            kommune = Person.Kommune(kommunenummer = "0301", kommunenavn = "OSLO")
        ),
        statsborgerskap = "NOR",
        kjønn = "MANN",
        adressebeskyttelse = null,
        skjermet = false
    )

    private val innholdJson = objectMapper.writeValueAsString(
        LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
            person = person,
            søknad = søknad,
            lukketSøknad = lukketSøknad
        )
    )

    @Test
    fun `journalfører en trukket søknad, og sender brev`() {
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
                genererPdf(innholdJson = innholdJson, pdfTemplate = PdfTemplate.TrukketSøknad)
            } doReturn pdf.right()
        }

        val dokArkivMock = mock<DokArkiv> {
            on {
                it.opprettJournalpost(
                    Journalpost.LukketSøknadJournalpostRequest(
                        person = person,
                        pdf = pdf,
                        sakId = sakId,
                        lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                            person = person,
                            søknad = søknad,
                            lukketSøknad = lukketSøknad
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
        ).journalførLukketSøknadOgSendBrev(sakId, søknad, lukketSøknad) shouldBe "en bestillings id".right()

        verify(sakServiceMock).hentSak(
            argThat<UUID> { it shouldBe sakId }
        )
        verify(personOppslagMock).person(
            argThat { it shouldBe sak.fnr },
        )
        verify(pdfGeneratorMock).genererPdf(
            argThat { it shouldBe innholdJson },
            argThat { it shouldBe PdfTemplate.TrukketSøknad }
        )
        verify(dokArkivMock).opprettJournalpost(
            argThat {
                it shouldBe Journalpost.LukketSøknadJournalpostRequest(
                    person = person,
                    pdf = pdf,
                    sakId = sakId,
                    lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                        person = person,
                        søknad = søknad,
                        lukketSøknad = lukketSøknad
                    )
                )
            }
        )

        verify(dokdistFordelingMock).bestillDistribusjon(
            argThat { it shouldBe "en journalpost id" }
        )
    }

    @Test
    fun `lager et brevutkast for lukket søknad`() {
        val pdfGeneratorMock = mock<PdfGenerator> {
            on {
                genererPdf(innholdJson = innholdJson, pdfTemplate = PdfTemplate.TrukketSøknad)
            } doReturn pdf.right()
        }
        val dokArkivMock = mock<DokArkiv> {}
        val dokdistFordelingMock = mock<DokDistFordeling> {}

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = mock(),
            dokArkiv = dokArkivMock,
            dokDistFordeling = dokdistFordelingMock,
            sakService = mock()
        ).lagBrev(
            LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold.lagTrukketSøknadBrevinnhold(
                søknad = søknad,
                person = person,
                lukketSøknad = lukketSøknad
            )
        ) shouldBe pdf.right()

        verify(pdfGeneratorMock).genererPdf(
            argThat { it shouldBe innholdJson },
            argThat { it shouldBe PdfTemplate.TrukketSøknad }
        )
    }
}
