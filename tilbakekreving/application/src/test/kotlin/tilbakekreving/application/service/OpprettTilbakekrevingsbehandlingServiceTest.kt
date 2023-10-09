package tilbakekreving.application.service

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.kravgrunnlag.sakMedUteståendeKravgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.opprett.OpprettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock
import java.util.UUID

class OpprettTilbakekrevingsbehandlingServiceTest {

    @Test
    fun `oppretter ny manuell tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))

        val correlationId = correlationId()
        val opprettetAv = saksbehandler
        val brukerroller = nonEmptyListOf(Brukerrolle.Saksbehandler)

        val sakMedKravgrunnlag = sakMedUteståendeKravgrunnlag(clock = clock)
        val sakId = sakMedKravgrunnlag.id
        val kravgrunnlag = sakMedKravgrunnlag.uteståendeKravgrunnlag!!
        val tilgangstyringService = mock<TilbakekrevingsbehandlingTilgangstyringService> {
            on { assertHarTilgangTilSak(any()) } doReturn Unit.right()
        }
        val sakService = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sakMedKravgrunnlag.right()
        }
        val personService = mock<PersonService> {
            on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
        }
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val mocks = mockedServices(
            tilgangstyringService = tilgangstyringService,
            sakService = sakService,
            clock = clock,
            personService = personService,
            oppgaveService = oppgaveService,
        )
        mocks.service().opprett(
            command = OpprettTilbakekrevingsbehandlingCommand(
                sakId = sakId,
                opprettetAv = opprettetAv,
                correlationId = correlationId,
                brukerroller = brukerroller,
                klientensSisteSaksversjon = Hendelsesversjon(1),
            ),
        ).shouldBeRight()

        verify(mocks.sakService).hentSak(
            argThat<UUID> { it shouldBe sakId },
        )
        verify(mocks.personService).hentAktørId(
            argThat { it shouldBe sakMedKravgrunnlag.fnr },
        )
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Tilbakekrevingsbehandling(
                    saksnummer = sakMedKravgrunnlag.saksnummer,
                    aktørId = AktørId(aktørId = "aktørId"),
                    tilordnetRessurs = saksbehandler,
                    clock = clock,
                )
            },
        )

        verify(mocks.tilbakekrevingsbehandlingRepo).lagre(
            argThat {
                it shouldBe OpprettetTilbakekrevingsbehandlingHendelse(
                    // Denne blir generert av domenet.
                    hendelseId = it.hendelseId,
                    sakId = sakId,
                    // vi bruker tikkende-klokke
                    hendelsestidspunkt = it.hendelsestidspunkt,
                    versjon = Hendelsesversjon(value = 2),
                    meta = DefaultHendelseMetadata(
                        correlationId = correlationId,
                        ident = saksbehandler,
                        brukerroller = brukerroller,
                    ),
                    // Denne blir generert av domenet.
                    id = it.id,
                    opprettetAv = opprettetAv,
                    kravgrunnlagsId = kravgrunnlag.eksternKravgrunnlagId,
                )
            },
            anyOrNull(),
        )
        verify(mocks.oppgaveHendelseRepo).lagre(
            argThat {
                it shouldBe OppgaveHendelse.opprettet(
                    // Denne blir generert av domenet.
                    hendelseId = it.hendelseId,
                    sakId = sakId,
                    versjon = Hendelsesversjon(value = 3),
                    // vi bruker tikkende-klokke
                    hendelsestidspunkt = it.hendelsestidspunkt,
                    meta = DefaultHendelseMetadata(
                        correlationId = correlationId,
                        ident = saksbehandler,
                        brukerroller = brukerroller,
                    ),
                    oppgaveId = OppgaveId(value = "oppgaveId"),
                    relaterteHendelser = it.relaterteHendelser.also {
                        it.size shouldBe 1
                    },
                )
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo = mock(),
        val tilgangstyringService: TilbakekrevingsbehandlingTilgangstyringService = mock(),
        val clock: Clock = fixedClock,
        val sakService: SakService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val oppgaveHendelseRepo: OppgaveHendelseRepo = mock(),
        val sessionFactory: TestSessionFactory = TestSessionFactory(),
    ) {
        fun service(): OpprettTilbakekrevingsbehandlingService =
            OpprettTilbakekrevingsbehandlingService(
                tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                tilgangstyring = tilgangstyringService,
                sakService = sakService,
                clock = clock,
                oppgaveService = oppgaveService,
                personService = personService,
                oppgaveHendelseRepo = oppgaveHendelseRepo,
                sessionFactory = sessionFactory,
            )

        fun verifyNoMoreInteractions() {
            verify(tilgangstyringService).assertHarTilgangTilSak(any())
            org.mockito.kotlin.verifyNoMoreInteractions(
                tilbakekrevingsbehandlingRepo,
                tilgangstyringService,
                sakService,
            )
        }
    }
}
