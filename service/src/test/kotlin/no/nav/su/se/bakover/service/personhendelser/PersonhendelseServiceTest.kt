package no.nav.su.se.bakover.service.personhendelser

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class PersonhendelseServiceTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    internal fun `kan lagre personhendelser`() {
        val sakId = UUID.randomUUID()
        val sakRepoMock = mock<SakRepo> {
            on { hentSakIdForIdenter(any()) } doReturn sakId
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakIdForIdenter(argThat { it shouldBe nyPersonhendelse.personidenter })
        verify(personhendelseRepoMock).lagre(
            personhendelse = argThat { it shouldBe nyPersonhendelse },
            id = any(),
            sakId = argThat { it shouldBe sakId },
        )
        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock, oppgaveServiceMock)
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har en sak`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSakIdForIdenter(any()) } doReturn null
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakIdForIdenter(argThat { it shouldBe nyPersonhendelse.personidenter })
        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock)
    }

    @Test
    internal fun `kan opprette oppgaver for lagrede personhendelser`() {
        val personhendelse = lagPersonhendelseTilknyttetSak()

        val sakRepoMock = mock<SakRepo> { }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenOppgave() } doReturn listOf(personhendelse)
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(personhendelseRepoMock).hentPersonhendelserUtenOppgave()
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    beskrivelse = "Dødsfall\n\tDødsdato: 2020-12-31",
                    aktørId = personhendelse.gjeldendeAktørId,
                )
            },
        )

        verify(personhendelseRepoMock).oppdaterOppgave(
            argThat { it shouldBe personhendelse.id },
            argThat { it shouldBe OppgaveId("oppgaveId") },
        )
        verifyNoMoreInteractions(oppgaveServiceMock, personhendelseRepoMock, sakRepoMock)
    }

    private fun lagNyPersonhendelse() = Personhendelse.Ny(
        gjeldendeAktørId = AktørId("123456b7890000"),
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        personidenter = nonEmptyListOf(Fnr.generer().toString(), "123456789010"),
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = LocalDate.now(fixedClock)),
        metadata = Personhendelse.Metadata(
            hendelseId = UUID.randomUUID().toString(),
            tidligereHendelseId = null,
            offset = 0,
            partisjon = 0,
            master = "FREG",
            key = "someKey",
        ),
    )

    private fun lagPersonhendelseTilknyttetSak() = Personhendelse.TilknyttetSak(
        gjeldendeAktørId = AktørId("123456b7890000"),
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = LocalDate.now(fixedClock)),
        id = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        sakId = UUID.randomUUID(),
        oppgaveId = null,
    )
}
