package no.nav.su.se.bakover.service.personhendelser

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.vedtak.toVedtaksammendrag
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import person.domain.PersonService
import java.util.UUID

internal class PersonhendelseServiceImplTest {
    @Test
    internal fun `kan lagre personhendelser`() {
        val sakId = UUID.randomUUID()
        val fnr = Fnr.generer()
        val sakRepoMock = mock<SakRepo> {
            on { hentSakInfoForEpsFnrFra(any(), any()) } doReturn emptyList()
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val vedtakServiceMock = mock<VedtakService> {
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
        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )
        val nyPersonhendelse = lagNyPersonhendelse(fnr = fnr)
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(vedtakServiceMock).hentForFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verify(sakRepoMock).hentSakInfoForEpsFnrFra(argThat { it shouldBe listOf(fnr) }, argThat { it shouldBe Måned.now(fixedClock) })
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
                    false,
                    fixedTidspunkt,
                )
            },
        )
        verifyNoMoreInteractions(
            personhendelseRepoMock,
            sakRepoMock,
            oppgaveServiceMock,
            vedtakServiceMock,
        )
    }

    @Test
    fun `lagrer personhendelse dersom hendelsen gjelder for eps`() {
        val fnr = Fnr.generer()
        val vedtakServiceMock = mock<VedtakService> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
        }
        val sakRepoMock = mock<SakRepo> {
            on { hentSakInfoForEpsFnrFra(any(), any()) } doReturn listOf(sakinfo)
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            doNothing().whenever(it).lagre(any<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>())
        }
        val oppgaveServiceMock: OppgaveService = mock()
        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )
        val nyPersonhendelse = lagNyPersonhendelse(fnr = fnr)
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(vedtakServiceMock).hentForFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verify(sakRepoMock).hentSakInfoForEpsFnrFra(argThat { it shouldBe listOf(fnr) }, argThat { it shouldBe Måned.now(fixedClock) })
        verify(personhendelseRepoMock).lagre(
            personhendelse = argThat<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> {
                it shouldBe nyPersonhendelse.tilknyttSak(
                    it.id,
                    SakInfo(sakId, saksnummer, fnr, Sakstype.UFØRE),
                    true,
                    fixedTidspunkt,
                )
            },
        )

        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock, oppgaveServiceMock, vedtakServiceMock)
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har vedtak`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSakInfoForEpsFnrFra(any(), any()) } doReturn emptyList()
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val vedtakServiceMock = mock<VedtakService> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
        }
        val personServiceMock = mock<PersonService>()
        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
            vedtakService = vedtakServiceMock,
        )
        val fnr = Fnr.generer()
        val nyPersonhendelse = lagNyPersonhendelse(fnr = fnr)
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)
        verify(vedtakServiceMock).hentForFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verify(sakRepoMock).hentSakInfoForEpsFnrFra(
            argThat { it shouldBe listOf(fnr) },
            argThat { it shouldBe Måned.now(fixedClock) },
        )
        verifyNoMoreInteractions(
            personhendelseRepoMock,
            sakRepoMock,
            oppgaveServiceMock,
            vedtakServiceMock,
            personServiceMock,
        )
    }

    @Test
    internal fun `kan opprette oppgaver for lagrede personhendelser`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val personhendelse = lagPersonhendelseTilknyttetSak(sakId = sak.id, saksnummer = sak.saksnummer)

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenOppgave() } doReturn listOf(personhendelse)
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val vedtakServiceMock = mock<VedtakService> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn sak.vedtakListe.toVedtaksammendrag()
        }

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenOppgave()
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    personhendelse = nonEmptySetOf(personhendelse),
                    fnr = sak.fnr,
                    clock = fixedClock,
                )
            },
        )

        verify(personhendelseRepoMock).lagre(
            argThat<List<Personhendelse.TilknyttetSak.SendtTilOppgave>> {
                it shouldBe listOf(personhendelse.tilSendtTilOppgave(OppgaveId("123")))
            },
        )
        verifyNoMoreInteractions(
            oppgaveServiceMock,
            personhendelseRepoMock,
            sakRepoMock,
            vedtakServiceMock,
        )
    }

    @Test
    internal fun `inkrementerer antall forsøk dersom oppretting av oppgave feiler`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val personhendelse = lagPersonhendelseTilknyttetSak(sakId = sak.id, saksnummer = sak.saksnummer)

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenOppgave() } doReturn listOf(personhendelse)
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val vedtakServiceMock = mock<VedtakService> {
            on { hentForFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn sak.vedtakListe.toVedtaksammendrag()
        }

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            vedtakService = vedtakServiceMock,
            clock = fixedClock,
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenOppgave()
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    personhendelse = nonEmptySetOf(personhendelse),
                    fnr = sak.fnr,
                    clock = fixedClock,
                )
            },
        )

        verify(personhendelseRepoMock).inkrementerAntallFeiledeForsøk(
            argThat<List<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>> {
                it shouldBe nonEmptyListOf(
                    personhendelse,
                )
            },
        )
        verifyNoMoreInteractions(
            oppgaveServiceMock,
            personhendelseRepoMock,
            sakRepoMock,
            vedtakServiceMock,
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
            eksternOpprettet = null,
        ),
    )

    private fun lagPersonhendelseTilknyttetSak(
        sakId: UUID = UUID.randomUUID(),
        saksnummer: Saksnummer = Saksnummer(2021),
    ) =
        Personhendelse.TilknyttetSak.IkkeSendtTilOppgave(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = fixedLocalDate),
            id = UUID.randomUUID(),
            saksnummer = saksnummer,
            sakId = sakId,
            metadata = Personhendelse.Metadata(
                hendelseId = UUID.randomUUID().toString(),
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "key",
                personidenter = listOf(UUID.randomUUID().toString()).toNonEmptyList(),
                eksternOpprettet = null,
            ),
            antallFeiledeForsøk = 0,
            opprettet = fixedTidspunkt,
            gjelderEps = false,
        )
}
