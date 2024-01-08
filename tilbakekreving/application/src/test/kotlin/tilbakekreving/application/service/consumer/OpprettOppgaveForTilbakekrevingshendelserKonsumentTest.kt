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
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nyOppgaveHendelse
import no.nav.su.se.bakover.test.nyOpprettetTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.PersonService
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.OpprettetTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class OpprettOppgaveForTilbakekrevingshendelserKonsumentTest {

    @Test
    fun `oppretter oppgave for hendelser som ikke har fått opprettet oppgave fra før`() {
        val (sak, _) = nySakUføre()
        val hendelseId = HendelseId.generer()
        val konsumenterRepo = mock<HendelsekonsumenterRepo> {
            on {
                hentUteståendeSakOgHendelsesIderForKonsumentOgType(any(), any(), anyOrNull(), anyOrNull())
            } doReturn mapOf(sakId to nonEmptyListOf(hendelseId))
        }
        val sakService = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val tilbakekrevingsbehandlingRepo = mock<TilbakekrevingsbehandlingRepo> {
            on {
                hentHendelse(any(), anyOrNull())
            } doReturn nyOpprettetTilbakekrevingsbehandlingHendelse(
                hendelseId = hendelseId,
                kravgrunnlagPåSakHendelseId = HendelseId.generer(),
            )
        }
        val personService = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("aktørId").right()
        }
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }
        val oppgaveHendelse = mock<OppgaveHendelseRepo> {
            on { hentHendelseForRelatert(any(), any(), anyOrNull()) } doReturn null
        }

        val mockedServices = mockedServices(
            sakService = sakService,
            oppgaveService = oppgaveService,
            personService = personService,
            hendelsekonsumenterRepo = konsumenterRepo,
            tilbakekrevingRepo = tilbakekrevingsbehandlingRepo,
            oppgaveHendelseRepo = oppgaveHendelse,
        )
        val correlationId = CorrelationId("Correlation-id")
        mockedServices.service().opprettOppgaver(correlationId)

        verify(konsumenterRepo).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            HendelseskonsumentId("OpprettOppgaveForTilbakekrevingsbehandlingHendelser"),
            OpprettetTilbakekrevingsbehandlingHendelsestype,
        )
        verify(sakService).hentSak(argShouldBe(sak.id))
        verify(tilbakekrevingsbehandlingRepo).hentHendelse(argShouldBe(hendelseId), anyOrNull())
        verify(oppgaveHendelse).hentHendelseForRelatert(argShouldBe(hendelseId), argShouldBe(sak.id), anyOrNull())
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
                it shouldBe OppgaveHendelse.Opprettet(
                    hendelseId = it.hendelseId,
                    hendelsestidspunkt = it.hendelsestidspunkt,
                    oppgaveId = OppgaveId("123"),
                    versjon = Hendelsesversjon(value = 2),
                    sakId = sak.id,
                    relaterteHendelser = listOf(hendelseId),
                    beskrivelse = "beskrivelse",
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                )
            },
            argThat {
                it shouldBe OppgaveHendelseMetadata(
                    correlationId = correlationId,
                    ident = null,
                    brukerroller = listOf(),
                    request = "request",
                    response = "response",
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

    @Test
    @Disabled("kan være IT-testen fanger denne")
    fun `dersom det allerede er en lagret oppgave hendelse, men finnes ikke i konsumenten, lagrer vi hendelsen i konsumenten og gjør ikke noe mer`() {
        val (sak, _) = nySakUføre()
        val opprettHendelse = nyOpprettetTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            kravgrunnlagPåSakHendelseId = HendelseId.generer(),
        )
        val konsumenterRepo = mock<HendelsekonsumenterRepo> {
            on {
                hentUteståendeSakOgHendelsesIderForKonsumentOgType(any(), any(), anyOrNull(), anyOrNull())
            } doReturn mapOf(sakId to nonEmptyListOf(opprettHendelse.hendelseId))
        }
        val sakService = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val tilbakekrevingsbehandlingRepo = mock<TilbakekrevingsbehandlingRepo> {
            on {
                hentHendelse(any(), anyOrNull())
            } doReturn opprettHendelse
        }

        val oppgaveHendelse = mock<OppgaveHendelseRepo> {
            on { hentHendelseForRelatert(any(), any(), anyOrNull()) } doReturn nyOppgaveHendelse(
                relaterteHendelser = listOf(opprettHendelse.hendelseId),
            )
        }

        val mockedServices = mockedServices(
            sakService = sakService,
            hendelsekonsumenterRepo = konsumenterRepo,
            tilbakekrevingRepo = tilbakekrevingsbehandlingRepo,
            oppgaveHendelseRepo = oppgaveHendelse,
        )
        mockedServices.service().opprettOppgaver(CorrelationId("Correlation-id"))

        verify(konsumenterRepo).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            HendelseskonsumentId("OpprettOppgaveForTilbakekrevingsbehandlingHendelser"),
            OpprettetTilbakekrevingsbehandlingHendelsestype,
        )
        verify(sakService).hentSak(argShouldBe(sak.id))
        verify(tilbakekrevingsbehandlingRepo).hentHendelse(argShouldBe(opprettHendelse.hendelseId), anyOrNull())
        verify(oppgaveHendelse).hentHendelseForRelatert(argShouldBe(opprettHendelse.hendelseId), argShouldBe(sak.id), anyOrNull())

        verify(mockedServices.hendelsekonsumenterRepo).lagre(
            argShouldBe(opprettHendelse.hendelseId),
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
