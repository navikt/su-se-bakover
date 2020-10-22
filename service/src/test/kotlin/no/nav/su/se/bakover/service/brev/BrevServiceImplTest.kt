package no.nav.su.se.bakover.service.brev

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.KunneIkkeGenererePdf
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Telefonnummer
import no.nav.su.se.bakover.domain.brev.Brevinnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.FantIkkeSak
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
        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "12345"),
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
    fun `genererer brev-pdf for tilsendt brevinnhold`() {
        val brevinnholdMock = mock<Brevinnhold>() {
            on { toJson() } doReturn innholdJson
            on { pdfTemplate() } doReturn PdfTemplate.TrukketSøknad
        }

        val pdfGeneratorMock = mock<PdfGenerator> {
            on {
                genererPdf(innholdJson = innholdJson, pdfTemplate = PdfTemplate.TrukketSøknad)
            } doReturn pdf.right()
        }

        createService(pdfGenerator = pdfGeneratorMock).lagBrev(brevinnholdMock) shouldBe pdf.right()

        verify(pdfGeneratorMock).genererPdf(
            argThat { it shouldBe innholdJson },
            argThat { it shouldBe PdfTemplate.TrukketSøknad }
        )
    }

    @Test
    fun `generering av brev-pdf feiler`() {
        val brevinnholdMock = mock<Brevinnhold>() {
            on { toJson() } doReturn innholdJson
            on { pdfTemplate() } doReturn PdfTemplate.TrukketSøknad
        }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on {
                genererPdf(innholdJson = innholdJson, pdfTemplate = PdfTemplate.TrukketSøknad)
            } doReturn KunneIkkeGenererePdf.left()
        }

        createService(pdfGenerator = pdfGeneratorMock).lagBrev(brevinnholdMock) shouldBe KunneIkkeLageBrev.KunneIkkeGenererePdf.left()
    }

    @Test
    fun `journalfører brev`() {
        val brevinnholdMock = mock<LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold>() {
            on { toJson() } doReturn "{}"
            on { pdfTemplate() } doReturn PdfTemplate.TrukketSøknad
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = any()) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { it.person(any()) } doReturn person.right()
        }

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any(), any()) } doReturn pdf.right()
        }

        val dokArkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn "journalpostId".right()
        }

        createService(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personOppslagMock,
            dokArkiv = dokArkivMock,
            sakService = sakServiceMock
        ).journalførBrev(brevinnholdMock, sakId) shouldBe "journalpostId".right()

        verify(sakServiceMock).hentSak(sakId)
        verify(personOppslagMock).person(fnr)
        verify(pdfGeneratorMock).genererPdf(brevinnholdMock.toJson(), brevinnholdMock.pdfTemplate())
        verify(dokArkivMock).opprettJournalpost(
            Journalpost.LukkSøknad(
                person = person,
                sakId = sakId.toString(),
                brevinnhold = brevinnholdMock,
                pdf = pdf
            )
        )
    }

    @Test
    fun `journalføring av brev feiler - sak ikke funnet`() {
        val brevinnholdMock = mock<LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold>() {
            on { toJson() } doReturn "{}"
            on { pdfTemplate() } doReturn PdfTemplate.TrukketSøknad
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = any()) } doReturn FantIkkeSak.left()
        }

        createService(sakService = sakServiceMock).journalførBrev(
            brevinnholdMock,
            sakId
        ) shouldBe KunneIkkeJournalføreBrev.FantIkkeSak.left()

        verify(sakServiceMock).hentSak(sakId)
    }

    @Test
    fun `journalføring av brev feiler - person ikke funnet`() {
        val brevinnholdMock = mock<LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold>() {
            on { toJson() } doReturn "{}"
            on { pdfTemplate() } doReturn PdfTemplate.TrukketSøknad
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = any()) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { it.person(any()) } doReturn PdlFeil.FantIkkePerson.left()
        }

        createService(
            sakService = sakServiceMock,
            personOppslag = personOppslagMock
        ).journalførBrev(
            brevinnholdMock,
            sakId
        ) shouldBe KunneIkkeJournalføreBrev.FantIkkePerson.left()

        verify(sakServiceMock).hentSak(sakId)
    }

    @Test
    fun `journalføring av brev feiler - lag pdf feiler`() {
        val brevinnholdMock = mock<LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold>() {
            on { toJson() } doReturn "{}"
            on { pdfTemplate() } doReturn PdfTemplate.TrukketSøknad
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = any()) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { it.person(any()) } doReturn person.right()
        }

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any(), any()) } doReturn KunneIkkeGenererePdf.left()
        }
        createService(
            sakService = sakServiceMock,
            personOppslag = personOppslagMock,
            pdfGenerator = pdfGeneratorMock
        ).journalførBrev(
            brevinnholdMock,
            sakId
        ) shouldBe KunneIkkeJournalføreBrev.KunneIkkeGenererePdf.left()

        verify(sakServiceMock).hentSak(sakId)
    }

    @Test
    fun `journalføring av brev feiler - opprett journalpost feiler`() {
        val brevinnholdMock = mock<LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold>() {
            on { toJson() } doReturn "{}"
            on { pdfTemplate() } doReturn PdfTemplate.TrukketSøknad
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = any()) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { it.person(any()) } doReturn person.right()
        }

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any(), any()) } doReturn pdf.right()
        }

        val dokArkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn ClientError(500, "").left()
        }

        createService(
            sakService = sakServiceMock,
            personOppslag = personOppslagMock,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock
        ).journalførBrev(
            brevinnholdMock,
            sakId
        ) shouldBe KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()

        verify(sakServiceMock).hentSak(sakId)
    }

    @Test
    fun `distribuerer brev`() {
        val dokumentDistribusjonMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any()) } doReturn "bestillingsId".right()
        }

        createService(
            dokDistFordeling = dokumentDistribusjonMock
        ).distribuerBrev("journalpostId") shouldBe "bestillingsId".right()

        verify(dokumentDistribusjonMock).bestillDistribusjon("journalpostId")
    }

    @Test
    fun `distribuerer brev feiler`() {
        val dokumentDistribusjonMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any()) } doReturn ClientError(500, "").left()
        }

        createService(
            dokDistFordeling = dokumentDistribusjonMock
        ).distribuerBrev("journalpostId") shouldBe KunneIkkeDistribuereBrev.left()

        verify(dokumentDistribusjonMock).bestillDistribusjon("journalpostId")
    }

    private fun createService(
        pdfGenerator: PdfGenerator = mock(),
        personOppslag: PersonOppslag = mock(),
        dokArkiv: DokArkiv = mock(),
        dokDistFordeling: DokDistFordeling = mock(),
        sakService: SakService = mock()
    ) = BrevServiceImpl(
        pdfGenerator = pdfGenerator,
        personOppslag = personOppslag,
        dokArkiv = dokArkiv,
        dokDistFordeling = dokDistFordeling,
        sakService = sakService
    )
}
