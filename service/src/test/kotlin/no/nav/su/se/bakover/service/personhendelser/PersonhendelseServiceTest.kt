package no.nav.su.se.bakover.service.personhendelser

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.vedtak.toVedtaksammendrag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.PersonService
import java.util.UUID

internal class PersonhendelseServiceTest {
    @Test
    internal fun `kan lagre personhendelser`() {
        val sakId = UUID.randomUUID()
        val fnr = Fnr.generer()
        val sakRepoMock = mock<SakRepo> {}
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn listOf(
                Vedtaksammendrag(
                    opprettet = fixedTidspunkt,
                    periode = år(2021),
                    fødselsnummer = fnr,
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                    sakId = sakId,
                    saksnummer = Saksnummer(2021),
                ),
            )
        }
        val personServiceMock = mock<PersonService>()
        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            vedtakRepo = vedtakRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = personServiceMock,
            clock = fixedClock,
        )
        val nyPersonhendelse = lagNyPersonhendelse(fnr = fnr)
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(vedtakRepoMock).hentForFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
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
        verifyNoMoreInteractions(
            personhendelseRepoMock,
            sakRepoMock,
            oppgaveServiceMock,
            vedtakRepoMock,
            personServiceMock,
        )
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har vedtak`() {
        val sakRepoMock = mock<SakRepo> {}
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
        }
        val personServiceMock = mock<PersonService>()
        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            personService = personServiceMock,
            clock = fixedClock,
            vedtakRepo = vedtakRepoMock,
        )
        val fnr = Fnr.generer()
        val nyPersonhendelse = lagNyPersonhendelse(fnr = fnr)
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)
        verify(vedtakRepoMock).hentForFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verifyNoMoreInteractions(
            personhendelseRepoMock,
            sakRepoMock,
            oppgaveServiceMock,
            vedtakRepoMock,
            personServiceMock,
        )
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
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("aktørId").right()
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn sak.vedtakListe.toVedtaksammendrag()
        }

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            vedtakRepo = vedtakRepoMock,
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
                it shouldBe personhendelse.tilSendtTilOppgave(OppgaveId("123"))
            },
        )
        verifyNoMoreInteractions(
            oppgaveServiceMock,
            personhendelseRepoMock,
            sakRepoMock,
            personServiceMock,
            vedtakRepoMock,
        )
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

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn sak.vedtakListe.toVedtaksammendrag()
        }

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            vedtakRepo = vedtakRepoMock,
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
        verifyNoMoreInteractions(
            oppgaveServiceMock,
            personhendelseRepoMock,
            sakRepoMock,
            personServiceMock,
            vedtakRepoMock,
        )
    }

    private fun lagNyPersonhendelse(
        fnr: Fnr = Fnr.generer(),
    ) = Personhendelse.IkkeTilknyttetSak(
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = fixedLocalDate),
        metadata = Personhendelse.Metadata(
            hendelseId = UUID.randomUUID().toString(),
            personidenter = nonEmptyListOf(fnr.toString(), "123456789010"),
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
                personidenter = listOf(UUID.randomUUID().toString()).toNonEmptyList(),
            ),
            antallFeiledeForsøk = 0,
        )
}
