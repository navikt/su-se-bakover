package no.nav.su.se.bakover.institusjonsopphold.application.service

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseActionRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyOppgaveHendelse
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
            on { hentTidligereInstHendelserForOpphold(any(), any()) } doReturn emptyList()
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
        verify(institusjonsoppholdHendelseRepo).hentTidligereInstHendelserForOpphold(
            argThat { it shouldBe sak.id },
            argThat { it shouldBe hendelse.oppholdId },
        )
        verify(institusjonsoppholdHendelseRepo).lagre(
            argThat {
                it shouldBe InstitusjonsoppholdHendelse(
                    sakId = sak.id,
                    hendelseId = it.hendelseId,
                    versjon = Hendelsesversjon(2),
                    eksterneHendelse = hendelse,
                    hendelsestidspunkt = fixedTidspunkt,
                )
            },
        )
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kaster exception dersom vi ikke finner sak ved opprettelse av oppgave for hendelse - blir fanget opp`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse()

        val hendelseActionRepo = mock<HendelseActionRepo> {
            on { hentSakOgHendelsesIderSomIkkeHarKjørtAction(any(), any(), anyOrNull(), anyOrNull()) } doReturn mapOf(
                sak.id to listOf(hendelse.hendelseId),
            )
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any()) } doReturn emptyList()
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
            hendelseActionRepo = hendelseActionRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
        )
        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser("jobbNavn")
        verify(hendelseActionRepo).hentSakOgHendelsesIderSomIkkeHarKjørtAction(
            argThat { it shouldBe "jobbNavn" },
            argThat { it shouldBe "INSTITUSJONSOPPHOLD" },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(sakRepo).hentSakInfo(argThat { it shouldBe sak.id })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kaster exception dersom vi ikke finner person ved opprettelse av oppgave for hendelse - blir fanget opp`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse()

        val hendelseActionRepo = mock<HendelseActionRepo> {
            on { hentSakOgHendelsesIderSomIkkeHarKjørtAction(any(), any(), anyOrNull(), anyOrNull()) } doReturn mapOf(
                sak.id to listOf(
                    hendelse.hendelseId,
                ),
            )
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any()) } doReturn emptyList()
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(hendelse))
        }
        val sakRepo = mock<SakRepo> {
            on { hentSakInfo(any<UUID>()) } doReturn sak.info()
        }
        val personService = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelseActionRepo = hendelseActionRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            personService = personService,
        )
        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser("jobbNavn")

        verify(hendelseActionRepo).hentSakOgHendelsesIderSomIkkeHarKjørtAction(
            argThat { it shouldBe "jobbNavn" },
            argThat { it shouldBe "INSTITUSJONSOPPHOLD" },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(sakRepo).hentSakInfo(argThat { it shouldBe sak.id })
        verify(personService).hentAktørId(argThat { it shouldBe sak.fnr })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppretter oppgaver for hendelser som ikke oppgaveId`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse()
        val person = person()

        val hendelseActionRepo = mock<HendelseActionRepo> {
            on { hentSakOgHendelsesIderSomIkkeHarKjørtAction(any(), any(), anyOrNull(), anyOrNull()) } doReturn
                mapOf(sak.id to listOf(hendelse.hendelseId))
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any()) } doReturn emptyList()
            doNothing().whenever(it).lagre(any(), any())
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(hendelse))
        }
        val sakRepo = mock<SakRepo> {
            on { hentSakInfo(any<UUID>()) } doReturn sak.info()
        }
        val personService = mock<PersonService> {
            on { hentAktørId(any()) } doReturn person.ident.aktørId.right()
        }
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val hendelseRepo = mock<HendelseRepo> {
            on { hentSisteVersjonFraEntitetId(any(), anyOrNull()) } doReturn Hendelsesversjon(2)
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelseActionRepo = hendelseActionRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            personService = personService,
            hendelseRepo = hendelseRepo,
            oppgaveService = oppgaveService,
        )
        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser("jobbNavn")

        verify(hendelseActionRepo).hentSakOgHendelsesIderSomIkkeHarKjørtAction(
            argThat { it shouldBe "jobbNavn" },
            argThat { it shouldBe "INSTITUSJONSOPPHOLD" },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(sakRepo).hentSakInfo(argThat { it shouldBe sak.id })
        verify(personService).hentAktørId(argThat { it shouldBe sak.fnr })

        verify(oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Institusjonsopphold(
                    sak.saksnummer, sak.type, person.ident.aktørId, fixedClock,
                )
            },
        )
        verify(hendelseRepo).hentSisteVersjonFraEntitetId(argThat { it shouldBe sak.id }, argThat { it shouldBe TestSessionFactory.transactionContext })
        verify(oppgaveHendelseRepo).lagre(
            argThat {
                it shouldBe OppgaveHendelse(
                    hendelseId = it.hendelseId,
                    tidligereHendelseId = null,
                    sakId = sak.id,
                    versjon = Hendelsesversjon(value = 3),
                    hendelsestidspunkt = fixedTidspunkt,
                    triggetAv = hendelse.hendelseId,
                    oppgaveId = OppgaveId("oppgaveId"),
                )
            },
            anyOrNull(),
        )
        verify(hendelseActionRepo).lagre(
            argThat<List<HendelseId>> { it shouldBe listOf(hendelse.hendelseId) },
            argThat { it shouldBe "jobbNavn" },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `lager ikke oppgave på en hendelse som har fått oppgave`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val hendelse = nyInstitusjonsoppholdHendelse()
        val oppgaveHendelse = nyOppgaveHendelse(hendelse)

        val hendelseActionRepo = mock<HendelseActionRepo> {
            on { hentSakOgHendelsesIderSomIkkeHarKjørtAction(any(), any(), anyOrNull(), anyOrNull()) } doReturn
                mapOf(sak.id to listOf(hendelse.hendelseId))
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any()) } doReturn listOf(oppgaveHendelse)
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(hendelse))
        }
        val testMocks = mockedServices(
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelseActionRepo = hendelseActionRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
        )
        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser("jobbNavn")

        verify(hendelseActionRepo).hentSakOgHendelsesIderSomIkkeHarKjørtAction(
            argThat { it shouldBe "jobbNavn" },
            argThat { it shouldBe "INSTITUSJONSOPPHOLD" },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `knytter ny hendelse med en tidligere dersom de har samme oppholdId`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val tidligereHendelse = nyInstitusjonsoppholdHendelse()
        val nyHendelse = nyEksternInstitusjonsoppholdHendelse(oppholdId = tidligereHendelse.eksterneHendelse.oppholdId)

        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            doNothing().whenever(it).lagre(any())
            on { hentTidligereInstHendelserForOpphold(any(), any()) } doReturn listOf(tidligereHendelse)
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(tidligereHendelse))
        }
        val sakRepo = mock<SakRepo> {
            on { hentSaker(any()) } doReturn listOf(sak)
        }
        val testMocks =
            mockedServices(
                sakRepo = sakRepo,
                institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            )
        testMocks.institusjonsoppholdService().process(nyHendelse)
        verify(sakRepo).hentSaker(argThat { it shouldBe fnr })
        verify(institusjonsoppholdHendelseRepo).hentTidligereInstHendelserForOpphold(
            argThat { it shouldBe sak.id },
            argThat { it shouldBe nyHendelse.oppholdId },
        )
        verify(institusjonsoppholdHendelseRepo).lagre(
            argThat {
                it shouldBe InstitusjonsoppholdHendelse(
                    sakId = sak.id,
                    hendelseId = it.hendelseId,
                    tidligereHendelseId = tidligereHendelse.hendelseId,
                    // hendelse 2 fordi saken i denne testen ikke har tatt høyde for tidligere hendelsen
                    versjon = Hendelsesversjon(2),
                    eksterneHendelse = nyHendelse,
                    hendelsestidspunkt = fixedTidspunkt,
                )
            },
        )
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `knytter oppgave hendelse til tidligere oppgaveHendelse dersom dem har samme oppholdId`() {
        val sak = søknadsbehandlingIverksattInnvilget().first
        val tidligereInstHendelse = nyInstitusjonsoppholdHendelse()
        val nyInstHendelse = nyInstitusjonsoppholdHendelse(
            tidligereHendelse = tidligereInstHendelse.hendelseId,
            versjon = tidligereInstHendelse.versjon.inc(),
        )
        val tidligereOppgaveHendelse = nyOppgaveHendelse(triggetAv = tidligereInstHendelse)
        val person = person()

        val hendelseActionRepo = mock<HendelseActionRepo> {
            on { hentSakOgHendelsesIderSomIkkeHarKjørtAction(any(), any(), anyOrNull(), anyOrNull()) } doReturn
                mapOf(sak.id to listOf(nyInstHendelse.hendelseId))
        }
        val oppgaveHendelseRepo = mock<OppgaveHendelseRepo> {
            on { hentForSak(any()) } doReturn listOf(tidligereOppgaveHendelse)
            doNothing().whenever(it).lagre(any(), any())
        }
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(
                nonEmptyListOf(
                    tidligereInstHendelse,
                    nyInstHendelse,
                ),
            )
        }
        val sakRepo = mock<SakRepo> {
            on { hentSakInfo(any<UUID>()) } doReturn sak.info()
        }
        val personService = mock<PersonService> {
            on { hentAktørId(any()) } doReturn person.ident.aktørId.right()
        }
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val hendelseRepo = mock<HendelseRepo> {
            on { hentSisteVersjonFraEntitetId(any(), anyOrNull()) } doReturn Hendelsesversjon(4)
        }
        val testMocks = mockedServices(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            hendelseActionRepo = hendelseActionRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            personService = personService,
            hendelseRepo = hendelseRepo,
            oppgaveService = oppgaveService,
        )
        testMocks.institusjonsoppholdService().opprettOppgaveForHendelser("jobbNavn")

        verify(hendelseActionRepo).hentSakOgHendelsesIderSomIkkeHarKjørtAction(
            argThat { it shouldBe "jobbNavn" },
            argThat { it shouldBe "INSTITUSJONSOPPHOLD" },
            anyOrNull(),
            anyOrNull(),
        )
        verify(oppgaveHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(sakRepo).hentSakInfo(argThat { it shouldBe sak.id })
        verify(personService).hentAktørId(argThat { it shouldBe sak.fnr })

        verify(oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Institusjonsopphold(
                    sak.saksnummer, sak.type, person.ident.aktørId, fixedClock,
                )
            },
        )

        verify(hendelseRepo).hentSisteVersjonFraEntitetId(argThat { it shouldBe sak.id }, anyOrNull())
        verify(oppgaveHendelseRepo).lagre(
            argThat {
                it shouldBe OppgaveHendelse(
                    hendelseId = it.hendelseId,
                    tidligereHendelseId = tidligereOppgaveHendelse.tidligereHendelseId,
                    sakId = sak.id,
                    versjon = Hendelsesversjon(value = 5),
                    hendelsestidspunkt = fixedTidspunkt,
                    triggetAv = nyInstHendelse.hendelseId,
                    oppgaveId = OppgaveId("oppgaveId"),
                )
            },
            anyOrNull(),
        )
        verify(hendelseActionRepo).lagre(
            argThat<List<HendelseId>> { it shouldBe listOf(nyInstHendelse.hendelseId) },
            argThat { it shouldBe "jobbNavn" },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        testMocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo = mock(),
        val oppgaveHendelseRepo: OppgaveHendelseRepo = mock(),
        val hendelseActionRepo: HendelseActionRepo = mock(),
        val hendelseRepo: HendelseRepo = mock(),
        val sakRepo: SakRepo = mock(),
        val sessionFactory: TestSessionFactory = TestSessionFactory(),
        val clock: Clock = fixedClock,
    ) {
        fun institusjonsoppholdService(): InstitusjonsoppholdService = InstitusjonsoppholdService(
            oppgaveService = oppgaveService,
            personService = personService,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            hendelseActionRepo = hendelseActionRepo,
            hendelseRepo = hendelseRepo,
            sakRepo = sakRepo,
            sessionFactory = sessionFactory,
            clock = clock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(
                oppgaveService,
                personService,
                institusjonsoppholdHendelseRepo,
                oppgaveHendelseRepo,
                hendelseActionRepo,
                hendelseRepo,
                sakRepo,
            )
        }
    }
}
