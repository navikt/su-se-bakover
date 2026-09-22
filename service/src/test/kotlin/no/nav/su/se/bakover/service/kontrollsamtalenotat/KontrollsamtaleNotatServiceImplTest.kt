package no.nav.su.se.bakover.service.kontrollsamtalenotat

import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleNotatServiceImpl
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.KunneIkkeHentePerson
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
                    KunneIkkeHentePerson.FantIkkePerson.left()
            },
            repository = mock(),
            pdfGenerator = mock(),
            forstesideGeneratorService = mock(),
            clock = fixedClock,
            journalførKontrollnotatClient = mock(),
            oppgaveService = oppgaveService,
            harRegistrerteKontrollsamtaler = { false },
        )
        service.lagre(
            sakId = sakId,
            kontrollsamtaleNotat = kontrollsamtaleNotat,
            sessionContext = null,
        )

        verify(oppgaveService).opprettOppgave(
            argThat { config ->
                config is OppgaveConfig.Kontrollsamtale &&
                    config.saksnummer == sakInfo.saksnummer &&
                    config.fnr == sakInfo.fnr &&
                    config.sakstype == sakInfo.type
            },
        )
    }
}
