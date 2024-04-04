package no.nav.su.se.bakover.service.personhendelser

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyPersonhendelseIkkeKnyttetTilSak
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.vedtak.toVedtaksammendrag
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSak
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSakVedtak
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
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
        val sakRepoMock = mock<SakRepo>()
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val vedtakServiceMock = mock<VedtakService> {
            on { hentForBrukerFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn listOf(
                VedtaksammendragForSak(
                    fødselsnummer = fnr,
                    sakId = sakId,
                    saksnummer = Saksnummer(2021),
                    vedtak = listOf(
                        VedtaksammendragForSak.Vedtak(
                            opprettet = fixedTidspunkt,
                            periode = år(2021),
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                ),
            )
            on { hentForEpsFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
        }
        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = mock(),
            personhendelseRepo = personhendelseRepoMock,
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )
        val nyPersonhendelse = lagNyPersonhendelse(fnr = fnr)
        personhendelseService.prosesserNyHendelse(Måned.now(fixedClock), nyPersonhendelse)

        verify(vedtakServiceMock).hentForBrukerFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verify(vedtakServiceMock).hentForEpsFødselsnumreOgFraOgMedMåned(
            argThat { it shouldBe listOf(fnr) },
            argThat { it shouldBe Måned.now(fixedClock) },
        )
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
        val vedtaksammendragForSak = vedtaksammendragForSak()
        val vedtakServiceMock = mock<VedtakService> {
            on { hentForBrukerFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
            on { hentForEpsFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn listOf(vedtaksammendragForSak)
        }
        val sakRepoMock = mock<SakRepo>()
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
        personhendelseService.prosesserNyHendelse(Måned.now(fixedClock), nyPersonhendelse)

        verify(vedtakServiceMock).hentForBrukerFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verify(vedtakServiceMock).hentForEpsFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verify(personhendelseRepoMock).lagre(
            personhendelse = argThat<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> {
                it shouldBe nyPersonhendelse.tilknyttSak(
                    it.id,
                    SakInfo(
                        vedtaksammendragForSak.sakId,
                        vedtaksammendragForSak.saksnummer,
                        vedtaksammendragForSak.fødselsnummer,
                        Sakstype.UFØRE,
                    ),
                    true,
                    fixedTidspunkt,
                )
            },
        )

        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock, oppgaveServiceMock, vedtakServiceMock)
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har vedtak`() {
        val sakRepoMock = mock<SakRepo>()
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val oppgaveServiceMock: OppgaveService = mock()
        val vedtakServiceMock = mock<VedtakService> {
            on { hentForBrukerFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
            on { hentForEpsFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
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
        personhendelseService.prosesserNyHendelse(Måned.now(fixedClock), nyPersonhendelse)
        verify(vedtakServiceMock).hentForBrukerFødselsnumreOgFraOgMedMåned(listOf(fnr), Måned.now(fixedClock))
        verify(vedtakServiceMock).hentForEpsFødselsnumreOgFraOgMedMåned(
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
            on { hentForBrukerFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn sak.vedtakListe.toVedtaksammendrag()
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
            on { hentForBrukerFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn sak.vedtakListe.toVedtaksammendrag()
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

    @Test
    fun `dryrunner personhendelser`() {
        val fnrPersonhendelse1 = Fnr.generer()
        val fnrPersonhendelse2 = Fnr.generer()
        val fnrPersonhendelse3 = Fnr.generer()
        val fnrPersonhendelse4 = Fnr.generer()

        val sammendrag1 = VedtaksammendragForSak(
            fødselsnummer = fnrPersonhendelse1,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
            vedtak = listOf(
                vedtaksammendragForSakVedtak(
                    opprettet = fixedTidspunkt,
                    periode = år(2021),
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                ),
            ),
        )
        val sammendrag2 = VedtaksammendragForSak(
            fødselsnummer = fnrPersonhendelse2,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2022),
            vedtak = listOf(
                vedtaksammendragForSakVedtak(
                    opprettet = fixedTidspunkt,
                    periode = år(2021),
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                ),
            ),
        )
        val sammendrag3 = VedtaksammendragForSak(
            fødselsnummer = fnrPersonhendelse3,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2023),
            vedtak = listOf(
                vedtaksammendragForSakVedtak(
                    opprettet = fixedTidspunkt,
                    periode = år(2021),
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                ),
            ),
        )

        /**
         * forventer første & tredje kall er fordi pershondelsen er direkte på søkeren
         */
        val vedtakServiceMock = mock<VedtakService> {
            on { hentForBrukerFødselsnumreOgFraOgMedMåned(any(), any()) }
                .thenReturn(listOf(sammendrag1))
                .thenReturn(emptyList())
                .thenReturn(listOf(sammendrag3))
                .thenReturn(emptyList())
                .thenReturn(emptyList())
            on { hentForEpsFødselsnumreOgFraOgMedMåned(any(), any()) }
                .thenReturn(emptyList())
                .thenReturn(listOf(sammendrag2))
                .thenReturn(emptyList())
                .thenReturn(emptyList())
                .thenReturn(emptyList())
        }

        val sakRepoMock = mock<SakRepo>()
        val personhendelseRepoMock = mock<PersonhendelseRepo> {}
        val oppgaveServiceMock = mock<OppgaveService> {}
        val personServiceMock = mock<PersonService>()

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )

        /**
         * merk at første 2 hendelser er knyttet til samme person
         * vi vil at koden skal gruppere disse
         */
        val personhendelser = listOf(
            nyPersonhendelseIkkeKnyttetTilSak(
                fnr = fnrPersonhendelse1,
                aktørId = AktørId("123"),
            ),
            nyPersonhendelseIkkeKnyttetTilSak(
                fnr = fnrPersonhendelse1,
                aktørId = AktørId("123"),
            ),
            nyPersonhendelseIkkeKnyttetTilSak(
                fnr = fnrPersonhendelse2,
                aktørId = AktørId("456"),
                hendelse = Personhendelse.Hendelse.Bostedsadresse,
            ),
            nyPersonhendelseIkkeKnyttetTilSak(
                fnr = fnrPersonhendelse3,
                aktørId = AktørId("789"),
                hendelse = Personhendelse.Hendelse.Kontaktadresse,
            ),
            nyPersonhendelseIkkeKnyttetTilSak(
                fnr = fnrPersonhendelse4,
                aktørId = AktørId("012"),
                hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(fixedLocalDate),
            ),
        )
        val actual = personhendelseService.dryRunPersonhendelser(Måned.now(fixedClock), personhendelser)
        withClue(actual.perHendelse) {
            actual.shouldBe(
                PersonhendelseServiceImpl.DryrunResult(
                    perHendelse = listOf(
                        PersonhendelseServiceImpl.DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseServiceImpl.PersonhendelseresultatBruker.TreffPåBruker(
                                saksnummer = sammendrag1.saksnummer,
                                fnr = sammendrag1.fødselsnummer,
                                identer = personhendelser[0].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                                identer = personhendelser[0].metadata.personidenter,
                            ),
                        ),
                        PersonhendelseServiceImpl.DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                                identer = personhendelser[1].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseServiceImpl.PersonhendelseresultatEps.TreffPåEnEllerFlereEps(
                                listOf(
                                    PersonhendelseServiceImpl.PersonhendelseresultatEps.TreffPåEps.AktivtVedtak(
                                        brukersSaksnummer = sammendrag2.saksnummer,
                                        brukersFnr = sammendrag2.fødselsnummer,
                                        identer = personhendelser[1].metadata.personidenter,
                                    ),
                                ),
                            ),
                        ),
                        PersonhendelseServiceImpl.DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseServiceImpl.PersonhendelseresultatBruker.TreffPåBruker(
                                saksnummer = sammendrag3.saksnummer,
                                fnr = sammendrag3.fødselsnummer,
                                identer = personhendelser[2].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                                identer = personhendelser[2].metadata.personidenter,
                            ),
                        ),
                        PersonhendelseServiceImpl.DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                                identer = personhendelser[3].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                                identer = personhendelser[3].metadata.personidenter,
                            ),
                        ),
                        PersonhendelseServiceImpl.DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                                identer = personhendelser[4].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                                identer = personhendelser[4].metadata.personidenter,
                            ),
                        ),
                    ),
                ),
            )
        }
        actual.antallOppgaver shouldBe 3
        actual.oppgaver shouldBe listOf(sammendrag1.saksnummer, sammendrag2.saksnummer, sammendrag3.saksnummer)
        actual.antallBruker shouldBe 2
        actual.bruker shouldBe listOf(
            PersonhendelseServiceImpl.PersonhendelseresultatBruker.TreffPåBruker(
                saksnummer = sammendrag1.saksnummer,
                fnr = sammendrag1.fødselsnummer,
                identer = personhendelser[0].metadata.personidenter,
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                identer = personhendelser[1].metadata.personidenter,
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatBruker.TreffPåBruker(
                saksnummer = sammendrag3.saksnummer,
                fnr = sammendrag3.fødselsnummer,
                identer = personhendelser[2].metadata.personidenter,
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                identer = personhendelser[3].metadata.personidenter,
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                identer = personhendelser[4].metadata.personidenter,
            ),
        )
        actual.antallEps shouldBe 1
        actual.eps shouldBe listOf(
            PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[0].metadata.personidenter,
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatEps.TreffPåEnEllerFlereEps(
                listOf(
                    PersonhendelseServiceImpl.PersonhendelseresultatEps.TreffPåEps.AktivtVedtak(
                        brukersSaksnummer = sammendrag2.saksnummer,
                        brukersFnr = sammendrag2.fødselsnummer,
                        identer = personhendelser[1].metadata.personidenter,
                    ),
                ),
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[2].metadata.personidenter,
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[3].metadata.personidenter,
            ),
            PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[4].metadata.personidenter,
            ),
        )
        actual.antallForkastet shouldBe 2
        actual.forkastet shouldBe listOf(
            PersonhendelseServiceImpl.DryrunResult.DryRunResultPerHendelse(
                resultatBruker = PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                    identer = personhendelser[3].metadata.personidenter,
                ),
                resultatEps = PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                    identer = personhendelser[3].metadata.personidenter,
                ),
            ),
            PersonhendelseServiceImpl.DryrunResult.DryRunResultPerHendelse(
                resultatBruker = PersonhendelseServiceImpl.PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                    identer = personhendelser[4].metadata.personidenter,
                ),
                resultatEps = PersonhendelseServiceImpl.PersonhendelseresultatEps.IkkeTreffPåEps(
                    identer = personhendelser[4].metadata.personidenter,
                ),
            ),
        )

        val fnrCaptorVedtakService = argumentCaptor<List<Fnr>>()
        verify(vedtakServiceMock, times(5)).hentForBrukerFødselsnumreOgFraOgMedMåned(
            fnrCaptorVedtakService.capture(),
            argThat { it shouldBe Måned.now(fixedClock) },
        )
        fnrCaptorVedtakService.allValues shouldBe listOf(
            listOf(fnrPersonhendelse1),
            listOf(fnrPersonhendelse1),
            listOf(fnrPersonhendelse2),
            listOf(fnrPersonhendelse3),
            listOf(fnrPersonhendelse4),
        )

        val fnrCaptorSakRepo = argumentCaptor<List<Fnr>>()
        verify(vedtakServiceMock, times(5)).hentForEpsFødselsnumreOgFraOgMedMåned(
            fnrCaptorSakRepo.capture(),
            argThat { it shouldBe Måned.now(fixedClock) },
        )

        fnrCaptorSakRepo.allValues shouldBe listOf(
            listOf(fnrPersonhendelse1),
            listOf(fnrPersonhendelse1),
            listOf(fnrPersonhendelse2),
            listOf(fnrPersonhendelse3),
            listOf(fnrPersonhendelse4),
        )

        verifyNoMoreInteractions(
            personhendelseRepoMock,
            sakRepoMock,
            oppgaveServiceMock,
            vedtakServiceMock,
            personServiceMock,
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
