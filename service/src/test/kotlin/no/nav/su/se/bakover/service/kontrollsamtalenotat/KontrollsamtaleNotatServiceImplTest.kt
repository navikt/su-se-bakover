package no.nav.su.se.bakover.service.kontrollsamtalenotat

import arrow.core.right
import dokument.domain.PdfGenerator
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.application.kontrollnotat.KontrollsamtaleNotatServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.person
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class KontrollsamtaleNotatServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val fnr = Fnr.generer()

    @Test
    fun `oppretter gosys oppgave når saken ikke har registrert kontrollsamtale`() {
        val sakInfo = SakInfo(
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            fnr = fnr,
            type = Sakstype.UFØRE,
        )

        val journalpostId = JournalpostId("journalpostId")
        val sakService = mock<SakService> {
            on { hentSakInfo(sakId) } doReturn sakInfo.right()
        }

        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn
                mock<OppgaveHttpKallResponse>().right()
        }

        val kontrollsamtaleNotat = KontrollsamtaleNotat(
            sakId = sakId,
            opprettet = fixedTidspunkt,
            personligOppmøte = true,
            fullmaktOgLegeerklæring = null,
            originalPass = true,
            gyldigPass = true,
            harVærtUtenlands = false,
            utenlandsoppholdDatoer = emptyList(),
            harPlanerOmUtenlandsreise = false,
            planlagteUtenlandsreiseDatoer = emptyList(),
            reiseDokumentasjon = false,
            økonomiskSituasjon = false,
            andreForhold = false,
            skatteOpplysninger = false,
            fritekst = null,
        )
        val service = KontrollsamtaleNotatServiceImpl(
            sakService = sakService,
            personService = mock {
                on { hentPerson(any(), any()) } doReturn
                    person(fnr = fnr).right()
            },
            repository = mock(),
            pdfGenerator = mock<PdfGenerator> {
                on { genererPdf(any()) } doReturn
                    PdfA("pdf-data".toByteArray()).right()
            },
            forstesideGeneratorService = mock(),
            clock = fixedClock,
            journalførKontrollnotatClient = mock {
                on { journalførKontrollnotat(any()) } doReturn
                    journalpostId.right()
            },
            oppgaveService = oppgaveService,
            kontrollsamtaleService = mock {
                on { hentKontrollsamtaler(sakId) } doReturn Kontrollsamtaler(
                    sakId = sakId,
                    kontrollsamtaler = emptyList(),
                )
            },
        )
        service.lagre(
            sakId = sakId,
            kontrollsamtaleNotat = kontrollsamtaleNotat,
        )

        verify(oppgaveService).opprettOppgave(
            argThat { config ->
                config is OppgaveConfig.KontrollnotatUtenKontrollsamtale &&
                    config.saksnummer == sakInfo.saksnummer &&
                    config.fnr == sakInfo.fnr &&
                    config.sakstype == sakInfo.type
                config.journalpostId == journalpostId
            },
        )
    }
}
