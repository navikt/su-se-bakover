package no.nav.su.se.bakover.service.personhendelser

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class PersonhendelseServiceTest {
    @Test
    internal fun `kan lagre personhendelser`() {
        val sakId = UUID.randomUUID()
        val fnr = Fnr.generer()
        val sakRepoMock = mock<SakRepo> {
            on { hentSakInfoForIdenter(any()) } doReturn SakInfo(sakId, Saksnummer(2021), fnr, Sakstype.UFØRE)
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = mock(),
            clock = fixedClock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakInfoForIdenter(argThat { it shouldBe nyPersonhendelse.metadata.personidenter })
        verify(personhendelseRepoMock).lagre(
            personhendelse = argThat<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> {
                it shouldBe nyPersonhendelse.tilknyttSak(
                    it.id,
                    SakInfo(
                        sakId,
                        Saksnummer(2021),
                        fnr,
                        Sakstype.UFØRE,
                    ),
                )
            },
        )
        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock, oppgaveServiceMock)
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har en sak`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSakInfoForIdenter(any()) } doReturn null
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = mock(),
            clock = fixedClock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakInfoForIdenter(argThat { it shouldBe nyPersonhendelse.metadata.personidenter })
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
            on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("aktørId").right()
        }

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = personServiceMock,
            clock = fixedClock,
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenOppgave()
        verify(personServiceMock).hentAktørIdMedSystembruker(argThat { it shouldBe sak.fnr })
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    personhendelsestype = personhendelse.hendelse,
                    aktørId = AktørId("aktørId"),
                    clock = fixedClock,
                )
            },
        )

        verify(personhendelseRepoMock).lagre(
            argThat<Personhendelse.TilknyttetSak.SendtTilOppgave> {
                it shouldBe personhendelse.tilSendtTilOppgave(OppgaveId("oppgaveId"))
            },
        )
        verifyNoMoreInteractions(oppgaveServiceMock, personhendelseRepoMock, sakRepoMock, personServiceMock)
    }

    @Test
    internal fun `inkrementerer antall forsøk dersom oppretting av oppgave feiler`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val personhendelse = lagPersonhendelseTilknyttetSak(sakId = sak.id)

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenOppgave() } doReturn listOf(personhendelse)
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveFeil.KunneIkkeOppretteOppgave.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("aktørId").right()
        }

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = personServiceMock,
            clock = fixedClock,
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenOppgave()
        verify(personServiceMock).hentAktørIdMedSystembruker(argThat { it shouldBe sak.fnr })
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    personhendelsestype = personhendelse.hendelse,
                    aktørId = AktørId("aktørId"),
                    clock = fixedClock,
                )
            },
        )

        verify(personhendelseRepoMock).inkrementerAntallFeiledeForsøk(argThat<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> { it shouldBe personhendelse })
        verifyNoMoreInteractions(oppgaveServiceMock, personhendelseRepoMock, sakRepoMock, personServiceMock)
    }

    private fun lagNyPersonhendelse() = Personhendelse.IkkeTilknyttetSak(
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = fixedLocalDate),
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

    private fun lagPersonhendelseTilknyttetSak(sakId: UUID = UUID.randomUUID()) =
        Personhendelse.TilknyttetSak.IkkeSendtTilOppgave(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = fixedLocalDate),
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
                personidenter = NonEmptyList.fromListUnsafe(listOf(UUID.randomUUID().toString())),
            ),
            antallFeiledeForsøk = 0,
        )
}
