package no.nav.su.se.bakover.institusjonsopphold.application.service

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseUtenOppgaveId
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.UUID

class InstitusjonsoppholdServiceTest {

    @Test
    fun `person har ikke sak blir prosessert men ingenting skjer`() {
        val fnrSomIkkeHarSak = Fnr.generer()
        val sakRepo = mock<SakRepo> { on { hentSaker(any()) } doReturn emptyList() }
        val testMocks = mockedServices(sakRepo = sakRepo)
        testMocks.institusjonsoppholdService()
            .process(nyEksternInstitusjonsoppholdHendelse(norskIdent = fnrSomIkkeHarSak))
        verify(sakRepo).hentSaker(argThat { it shouldBe fnrSomIkkeHarSak })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `person har sak blir prosessert og hendelse blir knyttet til sak`() {
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            doNothing().whenever(it).lagre(any())
        }
        val sak = søknadsbehandlingIverksattInnvilget().first
        val sakRepo = mock<SakRepo> {
            on { hentSaker(any()) } doReturn listOf(sak)
        }
        val testMocks =
            mockedServices(sakRepo = sakRepo, institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo)
        val hendelse = nyEksternInstitusjonsoppholdHendelse()
        testMocks.institusjonsoppholdService().process(hendelse)
        verify(sakRepo).hentSaker(argThat { it shouldBe fnr })
        verify(institusjonsoppholdHendelseRepo).lagre(
            argThat {
                it shouldBe InstitusjonsoppholdHendelse.UtenOppgaveId(
                    sakId = sak.id,
                    hendelseId = it.hendelseId,
                    versjon = Hendelsesversjon(1),
                    eksterneHendelse = hendelse,
                    hendelsestidspunkt = fixedTidspunkt,
                )
            },
        )
    }

    @Test
    fun `kaster exception dersom vi ikke finner sak ved opprettelse av oppgave for hendelse - blir fanget opp`() {
        val hendelse = nyInstitusjonsoppholdHendelseUtenOppgaveId()
        val sakRepo = mock<SakRepo> { on { hentSak(any<UUID>()) } doReturn null }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentHendelserUtenOppgaveId() } doReturn listOf(hendelse)
        }
        val testMocks =
            mockedServices(sakRepo = sakRepo, institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo)

        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser()
        verify(institusjonsoppholdHendelseRepo).hentHendelserUtenOppgaveId()
        verify(sakRepo).hentSak(argThat<UUID> { it shouldBe hendelse.sakId })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kaster exception dersom vi ikke finner person ved opprettelse av oppgave for hendelse - blir fanget opp`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelseUtenOppgaveId()
        val sakRepo = mock<SakRepo> { on { hentSak(any<UUID>()) } doReturn sak }
        val personService =
            mock<PersonService> { on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left() }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentHendelserUtenOppgaveId() } doReturn listOf(hendelse)
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            personService = personService,
        )
        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser()
        verify(institusjonsoppholdHendelseRepo).hentHendelserUtenOppgaveId()
        verify(sakRepo).hentSak(argThat<UUID> { it shouldBe hendelse.sakId })
        verify(personService).hentPerson(argThat { it shouldBe sak.fnr })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppretter oppgaver for hendelser som ikke oppgaveId`() {
        val person = person()
        val oppgaveId = OppgaveId("oppgaveid")
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelseUtenOppgaveId()
        val sakRepo = mock<SakRepo> { on { hentSak(any<UUID>()) } doReturn sak }
        val personService = mock<PersonService> { on { hentPerson(any()) } doReturn person.right() }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentHendelserUtenOppgaveId() } doReturn listOf(hendelse)
            doNothing().whenever(it).lagre(any())
        }
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn oppgaveId.right()
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            personService = personService,
            oppgaveService = oppgaveService,
        )
        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser()
        verify(institusjonsoppholdHendelseRepo).hentHendelserUtenOppgaveId()
        verify(sakRepo).hentSak(argThat<UUID> { it shouldBe hendelse.sakId })
        verify(personService).hentPerson(argThat { it shouldBe sak.fnr })
        verify(oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Institusjonsopphold(
                    sak.saksnummer,
                    sak.type,
                    person.ident.aktørId,
                    fixedClock,
                )
            },
        )
        verify(institusjonsoppholdHendelseRepo).lagre(
            argThat {
                it shouldBe InstitusjonsoppholdHendelse.MedOppgaveId(
                    hendelseId = it.hendelseId,
                    oppgaveId = oppgaveId,
                    hendelsestidspunkt = fixedTidspunkt,
                    tidligereHendelseId = hendelse.hendelseId,
                    versjon = Hendelsesversjon(2),
                    sakId = sak.id,
                    eksterneHendelse = nyEksternInstitusjonsoppholdHendelse(),
                )
            },
        )
        testMocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo = mock(),
        val sakRepo: SakRepo = mock(),
        val clock: Clock = fixedClock,
    ) {
        fun institusjonsoppholdService(): InstitusjonsoppholdService = InstitusjonsoppholdService(
            oppgaveService = oppgaveService,
            personService = personService,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            sakRepo = sakRepo,
            clock = clock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(oppgaveService, personService, institusjonsoppholdHendelseRepo, sakRepo)
        }
    }
}
