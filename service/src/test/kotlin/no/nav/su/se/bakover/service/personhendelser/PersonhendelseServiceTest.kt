package no.nav.su.se.bakover.service.personhendelser

import arrow.core.NonEmptyList
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
import no.nav.su.se.bakover.domain.sak.SakIdOgNummer
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
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
            on { hentSakIdOgNummerForIdenter(any()) } doReturn SakIdOgNummer(sakId, Saksnummer(2021))
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = mock(),
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakIdOgNummerForIdenter(argThat { it shouldBe nyPersonhendelse.metadata.personidenter })
        verify(personhendelseRepoMock).lagre(
            personhendelse = argThat { it shouldBe nyPersonhendelse.tilknyttSak(it.id, SakIdOgNummer(sakId, Saksnummer(2021))) },
        )
        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock, oppgaveServiceMock)
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har en sak`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSakIdOgNummerForIdenter(any()) } doReturn null
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = mock()
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakIdOgNummerForIdenter(argThat { it shouldBe nyPersonhendelse.metadata.personidenter })
        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock)
    }

    @Test
    internal fun `kan opprette oppgaver for lagrede personhendelser`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val personhendelse = lagPersonhendelseTilknyttetSak(sakId = sak.id)

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenOppgave() } doReturn listOf(personhendelse)
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("aktørId").right()
        }

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = personServiceMock
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenOppgave()
        verify(personServiceMock).hentAktørIdMedSystembruker(argThat { it shouldBe sak.fnr })
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    beskrivelse = "Dødsfall\n\tDødsdato: 2020-12-31",
                    aktørId = AktørId("aktørId"),
                )
            },
        )

        verify(personhendelseRepoMock).lagre(argThat { it shouldBe personhendelse.tilSendtTilOppgave(OppgaveId("oppgaveId")) })
        verifyNoMoreInteractions(oppgaveServiceMock, personhendelseRepoMock, sakRepoMock, personServiceMock)
    }

    private fun lagNyPersonhendelse() = Personhendelse.IkkeTilknyttetSak(
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = LocalDate.now(fixedClock)),
        metadata = Personhendelse.Metadata(
            hendelseId = UUID.randomUUID().toString(),
            personidenter = nonEmptyListOf(Fnr.generer().toString(), "123456789010"),
            tidligereHendelseId = null,
            offset = 0,
            partisjon = 0,
            master = "FREG",
            key = "someKey",
        ),
    )

    private fun lagPersonhendelseTilknyttetSak(sakId: UUID = UUID.randomUUID()) = Personhendelse.TilknyttetSak.IkkeSendtTilOppgave(
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = LocalDate.now(fixedClock)),
        id = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        sakId = sakId,
        metadata = Personhendelse.Metadata(
            hendelseId = UUID.randomUUID().toString(),
            tidligereHendelseId = null,
            offset = 0,
            partisjon = 0,
            master = "FREG",
            key = "key",
            personidenter = NonEmptyList.fromListUnsafe(listOf(UUID.randomUUID().toString()))
        )
    )
}
