package no.nav.su.se.bakover.institusjonsopphold.application.service

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak
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

class InstitusjonsoppholdServiceTest {

    @Test
    fun `person har ikke sak blir prosessert men ingenting skjer`() {
        val fnrSomIkkeHarSak = Fnr.generer()
        val sakRepo = mock<SakRepo> {
            on { hentSaker(any()) } doReturn emptyList()
        }
        val testMocks = InstitusjonsoppholdServiceTestMocks(sakRepo = sakRepo)
        testMocks.institusjonsoppholdService().process(
            nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak(norskIdent = fnrSomIkkeHarSak),
        )
        verify(sakRepo).hentSaker(argThat { it shouldBe fnrSomIkkeHarSak })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `person har sak blir prosessert og hendelse blir knyttet til sak`() {
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            doNothing().whenever(it).lagre(any())
        }
        val sak = søknadsbehandlingIverksattInnvilget().first
        val sakRepo = mock<SakRepo> { on { hentSaker(any()) } doReturn listOf(sak) }
        val testMocks = InstitusjonsoppholdServiceTestMocks(
            sakRepo = sakRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
        )
        val hendelse = nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak()
        testMocks.institusjonsoppholdService().process(hendelse)
        verify(sakRepo).hentSaker(argThat { it shouldBe fnr })
        verify(institusjonsoppholdHendelseRepo).lagre(
            argThat {
                it shouldBe InstitusjonsoppholdHendelse.KnyttetTilSak.UtenOppgaveId(
                    sakId = sak.id,
                    ikkeKnyttetTilSak = hendelse,
                )
            },
        )
    }

    private data class InstitusjonsoppholdServiceTestMocks(
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
