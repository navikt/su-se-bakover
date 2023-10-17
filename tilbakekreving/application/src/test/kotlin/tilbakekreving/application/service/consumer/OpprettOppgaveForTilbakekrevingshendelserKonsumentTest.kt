package tilbakekreving.application.service.consumer

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nyOpprettetTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.PersonService
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.OpprettTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class OpprettOppgaveForTilbakekrevingshendelserKonsumentTest {

    @Test
    fun `oppretter oppgave for hendelser som ikke har fått opprettet oppgave fra før`() {
        val (sak, _) = nySakUføre()
        val hendelseId = HendelseId.generer()
        val meta = DefaultHendelseMetadata(
            correlationId = CorrelationId("Correlation-id"),
            ident = saksbehandler,
            brukerroller = emptyList(),
        )
        val konsumenterRepo = mock<HendelsekonsumenterRepo> {
            on {
                hentUteståendeSakOgHendelsesIderForKonsumentOgType(any(), any(), anyOrNull(), anyOrNull())
            } doReturn mapOf(sakId to nonEmptyListOf(hendelseId))
        }
        val sakService = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val hendelseRepo = mock<HendelseRepo> {
            on { hentSisteVersjonFraEntitetId(any(), anyOrNull()) } doReturn Hendelsesversjon(4)
        }
        val tilbakekrevingsbehandlingRepo = mock<TilbakekrevingsbehandlingRepo> {
            on {
                hentHendelse(any(), anyOrNull())
            } doReturn nyOpprettetTilbakekrevingsbehandlingHendelse(
                hendelseId = hendelseId, meta = meta,
            )
        }
        val personService = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("aktørId").right()
        }
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveId("123").right()
        }

        val mockedServices = mockedServices(
            sakService = sakService,
            oppgaveService = oppgaveService,
            personService = personService,
            hendelseRepo = hendelseRepo,
            hendelsekonsumenterRepo = konsumenterRepo,
            tilbakekrevingRepo = tilbakekrevingsbehandlingRepo,
        )
        mockedServices.service().opprettOppgaver(CorrelationId("Correlation-id"))

        verify(konsumenterRepo).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            HendelseskonsumentId("OpprettOppgaveForTilbakekrevingsbehandlingHendelser"),
            OpprettTilbakekrevingsbehandlingHendelsestype,
        )
        verify(sakService).hentSak(argShouldBe(sak.id))
        verify(hendelseRepo).hentSisteVersjonFraEntitetId(argShouldBe(sak.id), anyOrNull())
        verify(tilbakekrevingsbehandlingRepo).hentHendelse(argShouldBe(hendelseId), anyOrNull())
        verify(personService).hentAktørIdMedSystembruker(argShouldBe(sak.fnr))
        verify(oppgaveService).opprettOppgaveMedSystembruker(
            argShouldBe(
                OppgaveConfig.Tilbakekrevingsbehandling(
                    saksnummer = sak.saksnummer,
                    aktørId = AktørId(aktørId = "aktørId"),
                    tilordnetRessurs = saksbehandler,
                    clock = fixedClock,
                ),
            ),
        )
        verify(mockedServices.oppgaveHendelseRepo).lagre(
            argThat {
                it shouldBe OppgaveHendelse.opprettet(
                    hendelseId = it.hendelseId,
                    hendelsestidspunkt = it.hendelsestidspunkt,
                    oppgaveId = OppgaveId("123"),
                    versjon = Hendelsesversjon(value = 5),
                    sakId = sak.id,
                    relaterteHendelser = listOf(hendelseId),
                    meta = DefaultHendelseMetadata.fraCorrelationId(CorrelationId("Correlation-id")),
                )
            },
            anyOrNull(),
        )
        verify(mockedServices.hendelsekonsumenterRepo).lagre(
            argShouldBe(hendelseId),
            argThat { it shouldBe mockedServices.service().konsumentId },
            anyOrNull(),
        )
        mockedServices.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val sakService: SakService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val oppgaveHendelseRepo: OppgaveHendelseRepo = mock(),
        val hendelseRepo: HendelseRepo = mock(),
        val hendelsekonsumenterRepo: HendelsekonsumenterRepo = mock(),
        val tilbakekrevingRepo: TilbakekrevingsbehandlingRepo,
        val sessionFactory: TestSessionFactory = TestSessionFactory(),
        val clock: Clock = fixedClock,
    ) {
        fun service(): OpprettOppgaveForTilbakekrevingshendelserKonsument =
            OpprettOppgaveForTilbakekrevingshendelserKonsument(
                sakService = sakService,
                clock = clock,
                personService = personService,
                sessionFactory = sessionFactory,
                oppgaveService = oppgaveService,
                tilbakekrevingsbehandlingHendelseRepo = tilbakekrevingRepo,
                oppgaveHendelseRepo = oppgaveHendelseRepo,
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            )

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                sakService,
                oppgaveService,
                personService,
                oppgaveHendelseRepo,
                hendelseRepo,
                hendelsekonsumenterRepo,
                tilbakekrevingRepo,
            )
        }
    }
}
