package no.nav.su.se.bakover.institusjonsopphold.application.service

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyOppgaveHendelse
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import person.domain.KunneIkkeHentePerson
import java.time.Clock
import java.util.UUID

class OpprettOppgaverForInstitusjonsoppholdshendelserTest {

    @Test
    fun `returns early dersom vi ikke finner sak ved opprettelse av oppgave for hendelse`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse()
        val correlationId = correlationId()

        val hendelsekonsumenterRepo = mock<HendelsekonsumenterRepo> {
            on {
                hentUteståendeSakOgHendelsesIderForKonsumentOgType(
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                )
            } doReturn mapOf(
                sak.id to nonEmptyListOf(hendelse.hendelseId),
            )
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn emptyList()
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(hendelse))
        }
        val sakRepo = mock<SakRepo> {
            on { hentSakInfo(any<UUID>()) } doReturn null
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
        )
        testMocks.createService().opprettOppgaverForHendelser(correlationId)
        // Verifiserer at vi ikke har gjort noen sideeffekter
        verify(hendelsekonsumenterRepo).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            argThat { it shouldBe HendelseskonsumentId("OpprettOppgaverForInstitusjonsoppholdshendelser") },
            argThat { it shouldBe Hendelsestype("INSTITUSJONSOPPHOLD") },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id }, anyOrNull())
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(sakRepo).hentSakInfo(argThat { it shouldBe sak.id })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `returns early dersom vi ikke finner person ved opprettelse av oppgave for hendelse`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse()
        val correlationId = correlationId()
        val hendelsekonsumenterRepo = mock<HendelsekonsumenterRepo> {
            on {
                hentUteståendeSakOgHendelsesIderForKonsumentOgType(
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                )
            } doReturn mapOf(
                sak.id to nonEmptyListOf(
                    hendelse.hendelseId,
                ),
            )
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn emptyList()
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(hendelse))
        }
        val sakRepo = mock<SakRepo> {
            on { hentSakInfo(any<UUID>()) } doReturn sak.info()
        }
        val personService = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val hendelseRepo = mock<HendelseRepo> {
            on { hentSisteVersjonFraEntitetId(any(), anyOrNull()) } doReturn Hendelsesversjon(2)
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            personService = personService,
            hendelseRepo = hendelseRepo,
        )
        testMocks.createService().opprettOppgaverForHendelser(correlationId)
        // Verifiserer at vi ikke har gjort noen sideeffekter
        verify(hendelsekonsumenterRepo).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            argThat { it shouldBe HendelseskonsumentId("OpprettOppgaverForInstitusjonsoppholdshendelser") },
            argThat { it shouldBe Hendelsestype("INSTITUSJONSOPPHOLD") },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id }, anyOrNull())
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(sakRepo).hentSakInfo(argThat { it shouldBe sak.id })
        verify(personService).hentAktørIdMedSystembruker(argThat { it shouldBe sak.fnr })
        verify(hendelseRepo).hentSisteVersjonFraEntitetId(any(), anyOrNull())
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppretter oppgaver for insthendelser som mangler oppgave`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse()
        val person = person()
        val correlationId = correlationId()

        val hendelsekonsumenterRepo = mock<HendelsekonsumenterRepo> {
            on { hentUteståendeSakOgHendelsesIderForKonsumentOgType(any(), any(), anyOrNull(), anyOrNull()) } doReturn
                mapOf(sak.id to nonEmptyListOf(hendelse.hendelseId))
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn emptyList()
            doNothing().whenever(it).lagre(any(), any())
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(hendelse))
        }
        val sakRepo = mock<SakRepo> {
            on { hentSakInfo(any<UUID>()) } doReturn sak.info()
        }
        val personService = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn person.ident.aktørId.right()
        }
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val hendelseRepo = mock<HendelseRepo> {
            on { hentSisteVersjonFraEntitetId(any(), anyOrNull()) } doReturn Hendelsesversjon(2)
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            personService = personService,
            hendelseRepo = hendelseRepo,
            oppgaveService = oppgaveService,
        )
        testMocks.createService().opprettOppgaverForHendelser(correlationId)

        verify(hendelsekonsumenterRepo).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            argThat { it shouldBe HendelseskonsumentId("OpprettOppgaverForInstitusjonsoppholdshendelser") },
            argThat { it shouldBe Hendelsestype("INSTITUSJONSOPPHOLD") },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id }, anyOrNull())
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(sakRepo).hentSakInfo(argThat { it shouldBe sak.id })
        verify(personService).hentAktørIdMedSystembruker(argThat { it shouldBe sak.fnr })

        verify(oppgaveService).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Institusjonsopphold(
                    sak.saksnummer, sak.type, person.ident.aktørId, fixedClock,
                )
            },
        )
        verify(hendelseRepo).hentSisteVersjonFraEntitetId(
            argThat { it shouldBe sak.id },
            eq(null),
        )
        verify(oppgaveHendelseRepo).lagre(
            argThat {
                it shouldBe OppgaveHendelse.opprettet(
                    hendelseId = it.hendelseId,
                    sakId = sak.id,
                    versjon = Hendelsesversjon(value = 3),
                    hendelsestidspunkt = fixedTidspunkt,
                    oppgaveId = OppgaveId("oppgaveId"),
                    meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                    relaterteHendelser = listOf(hendelse.hendelseId),
                )
            },
            anyOrNull(),
        )
        verify(hendelsekonsumenterRepo).lagre(
            argThat<List<HendelseId>> { it shouldBe listOf(hendelse.hendelseId) },
            argThat { it shouldBe HendelseskonsumentId("OpprettOppgaverForInstitusjonsoppholdshendelser") },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `lager ikke oppgave på en hendelse som har fått oppgave`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse(
            sakId = sak.id,
        )
        val oppgaveHendelse = nyOppgaveHendelse(
            sakId = sak.id,
            relaterteHendelser = listOf(hendelse.hendelseId),
            nesteVersjon = Hendelsesversjon(3),
        )
        val correlationId = correlationId()
        val hendelsekonsumenterRepo = mock<HendelsekonsumenterRepo> {
            on { hentUteståendeSakOgHendelsesIderForKonsumentOgType(any(), any(), anyOrNull(), anyOrNull()) } doReturn
                mapOf(sak.id to nonEmptyListOf(hendelse.hendelseId))
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn listOf(oppgaveHendelse)
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(hendelse))
        }
        val testMocks = mockedServices(
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
        )
        testMocks.createService().opprettOppgaverForHendelser(correlationId)

        val hendelseskonsumentId = HendelseskonsumentId("OpprettOppgaverForInstitusjonsoppholdshendelser")
        verify(hendelsekonsumenterRepo).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            argThat { it shouldBe hendelseskonsumentId },
            argThat { it shouldBe Hendelsestype("INSTITUSJONSOPPHOLD") },
            anyOrNull(),
            anyOrNull(),
        )
        // Passer på at vi markerer hendelsen som prosessert samtidig som det ikke skal lages oppgave eller lagre en oppgavehendelse.
        verify(hendelsekonsumenterRepo).lagre(
            hendelser = argThat { it shouldBe nonEmptyListOf(hendelse.hendelseId) },
            konsumentId = argThat { it shouldBe hendelseskonsumentId },
            context = anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id }, anyOrNull())
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        testMocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo = mock(),
        val oppgaveHendelseRepo: OppgaveHendelseRepo = mock(),
        val hendelsekonsumenterRepo: HendelsekonsumenterRepo = mock(),
        val hendelseRepo: HendelseRepo = mock(),
        val sakRepo: SakRepo = mock(),
        val sessionFactory: TestSessionFactory = TestSessionFactory(),
        val clock: Clock = fixedClock,
    ) {
        fun createService(): OpprettOppgaverForInstitusjonsoppholdshendelser =
            OpprettOppgaverForInstitusjonsoppholdshendelser(
                institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
                sakRepo = sakRepo,
                clock = clock,
                oppgaveService = oppgaveService,
                personService = personService,
                oppgaveHendelseRepo = oppgaveHendelseRepo,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                hendelseRepo = hendelseRepo,
                sessionFactory = sessionFactory,
            )

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                oppgaveService,
                personService,
                institusjonsoppholdHendelseRepo,
                oppgaveHendelseRepo,
                hendelsekonsumenterRepo,
                hendelseRepo,
                sakRepo,
            )
        }
    }
}
