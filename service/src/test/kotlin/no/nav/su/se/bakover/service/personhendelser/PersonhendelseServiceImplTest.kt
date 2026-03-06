package no.nav.su.se.bakover.service.personhendelser

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
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
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.vedtak.toVedtaksammendrag
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSak
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSakVedtak
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import person.domain.AdresseopplysningerMedMetadata
import person.domain.KunneIkkeHentePerson
import person.domain.PersonOppslag
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
                            sakstype = Sakstype.UFØRE,
                            epsFnr = emptyList(),
                        ),
                    ),
                ),
            )
            on { hentForEpsFødselsnumreOgFraOgMedMåned(any(), any()) } doReturn emptyList()
        }
        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = mock(),
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
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
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
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
        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
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
            on { hentPersonhendelserUtenPdlVurdering() } doReturn emptyList()
            on { hentPersonhendelserKlareForOppgave() } doReturn listOf(personhendelse)
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
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenPdlVurdering()
        verify(personhendelseRepoMock).hentPersonhendelserKlareForOppgave()
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    personhendelse = nonEmptySetOf(personhendelse),
                    fnr = sak.fnr,
                    clock = fixedClock,
                    sakstype = sak.type,
                )
            },
        )

        verify(personhendelseRepoMock).lagre(
            argThat<List<Personhendelse.TilknyttetSak.SendtTilOppgave>> {
                it shouldBe listOf(personhendelse.tilSendtTilOppgave(oppgaveId))
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
    internal fun `oppretter separate oppgaver per hendelsestype for samme sak`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val dødsfall = lagPersonhendelseTilknyttetSak(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
        )
        val utflytting = lagPersonhendelseTilknyttetSak(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(fixedLocalDate),
        )

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenPdlVurdering() } doReturn emptyList()
            on { hentPersonhendelserKlareForOppgave() } doReturn listOf(dødsfall, utflytting)
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
            vedtakService = mock(),
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )

        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock, times(1)).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenPdlVurdering()
        verify(personhendelseRepoMock).hentPersonhendelserKlareForOppgave()
        val oppgaveCaptor = argumentCaptor<OppgaveConfig>()
        verify(oppgaveServiceMock, times(2)).opprettOppgaveMedSystembruker(oppgaveCaptor.capture())
        oppgaveCaptor.allValues.map { it as OppgaveConfig.Personhendelse }.map { it.personhendelse.toSet() } shouldBe listOf(
            setOf(dødsfall),
            setOf(utflytting),
        )

        val lagretCaptor = argumentCaptor<List<Personhendelse.TilknyttetSak.SendtTilOppgave>>()
        verify(personhendelseRepoMock, times(2)).lagre(lagretCaptor.capture())
        lagretCaptor.allValues.flatten().map { it.id }.toSet() shouldBe setOf(dødsfall.id, utflytting.id)
        verifyNoMoreInteractions(
            oppgaveServiceMock,
            personhendelseRepoMock,
            sakRepoMock,
        )
    }

    @Test
    internal fun `slår ikke sammen bostedsadressehendelser for samme sak`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val bostedsadresse1 = lagPersonhendelseTilknyttetSak(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
        )
        val bostedsadresse2 = lagPersonhendelseTilknyttetSak(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
        )

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenPdlVurdering() } doReturn emptyList()
            on { hentPersonhendelserKlareForOppgave() } doReturn listOf(bostedsadresse1, bostedsadresse2)
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
            vedtakService = mock(),
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )

        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock, times(1)).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenPdlVurdering()
        verify(personhendelseRepoMock).hentPersonhendelserKlareForOppgave()
        val oppgaveCaptor = argumentCaptor<OppgaveConfig>()
        verify(oppgaveServiceMock, times(2)).opprettOppgaveMedSystembruker(oppgaveCaptor.capture())
        oppgaveCaptor.allValues.map { it as OppgaveConfig.Personhendelse }.map { it.personhendelse.toSet() } shouldBe listOf(
            setOf(bostedsadresse1),
            setOf(bostedsadresse2),
        )

        val lagretCaptor = argumentCaptor<List<Personhendelse.TilknyttetSak.SendtTilOppgave>>()
        verify(personhendelseRepoMock, times(2)).lagre(lagretCaptor.capture())
        lagretCaptor.allValues.flatten().map { it.id }.toSet() shouldBe setOf(bostedsadresse1.id, bostedsadresse2.id)
        verifyNoMoreInteractions(
            oppgaveServiceMock,
            personhendelseRepoMock,
            sakRepoMock,
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
            on { hentPersonhendelserUtenPdlVurdering() } doReturn emptyList()
            on { hentPersonhendelserKlareForOppgave() } doReturn listOf(personhendelse)
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
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
            oppgaveServiceImpl = oppgaveServiceMock,
            vedtakService = vedtakServiceMock,
            clock = fixedClock,
        )
        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(personhendelseRepoMock).hentPersonhendelserUtenPdlVurdering()
        verify(personhendelseRepoMock).hentPersonhendelserKlareForOppgave()
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Personhendelse(
                    saksnummer = personhendelse.saksnummer,
                    personhendelse = nonEmptySetOf(personhendelse),
                    fnr = sak.fnr,
                    clock = fixedClock,
                    sakstype = sak.type,
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
        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
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
                hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            ),
            nyPersonhendelseIkkeKnyttetTilSak(
                fnr = fnrPersonhendelse3,
                aktørId = AktørId("789"),
                hendelse = Personhendelse.Hendelse.Kontaktadresse(),
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
                DryrunResult(
                    perHendelse = listOf(
                        DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseresultatBruker.TreffPåBruker(
                                saksnummer = sammendrag1.saksnummer,
                                fnr = sammendrag1.fødselsnummer,
                                identer = personhendelser[0].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseresultatEps.IkkeTreffPåEps(
                                identer = personhendelser[0].metadata.personidenter,
                            ),
                        ),
                        DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                                identer = personhendelser[1].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseresultatEps.TreffPåEnEllerFlereEps(
                                listOf(
                                    PersonhendelseresultatEps.TreffPåEps.AktivtVedtak(
                                        brukersSaksnummer = sammendrag2.saksnummer,
                                        brukersFnr = sammendrag2.fødselsnummer,
                                        identer = personhendelser[1].metadata.personidenter,
                                    ),
                                ),
                            ),
                        ),
                        DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseresultatBruker.TreffPåBruker(
                                saksnummer = sammendrag3.saksnummer,
                                fnr = sammendrag3.fødselsnummer,
                                identer = personhendelser[2].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseresultatEps.IkkeTreffPåEps(
                                identer = personhendelser[2].metadata.personidenter,
                            ),
                        ),
                        DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                                identer = personhendelser[3].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseresultatEps.IkkeTreffPåEps(
                                identer = personhendelser[3].metadata.personidenter,
                            ),
                        ),
                        DryrunResult.DryRunResultPerHendelse(
                            resultatBruker = PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                                identer = personhendelser[4].metadata.personidenter,
                            ),
                            resultatEps = PersonhendelseresultatEps.IkkeTreffPåEps(
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
            PersonhendelseresultatBruker.TreffPåBruker(
                saksnummer = sammendrag1.saksnummer,
                fnr = sammendrag1.fødselsnummer,
                identer = personhendelser[0].metadata.personidenter,
            ),
            PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                identer = personhendelser[1].metadata.personidenter,
            ),
            PersonhendelseresultatBruker.TreffPåBruker(
                saksnummer = sammendrag3.saksnummer,
                fnr = sammendrag3.fødselsnummer,
                identer = personhendelser[2].metadata.personidenter,
            ),
            PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                identer = personhendelser[3].metadata.personidenter,
            ),
            PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                identer = personhendelser[4].metadata.personidenter,
            ),
        )
        actual.antallEps shouldBe 1
        actual.eps shouldBe listOf(
            PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[0].metadata.personidenter,
            ),
            PersonhendelseresultatEps.TreffPåEnEllerFlereEps(
                listOf(
                    PersonhendelseresultatEps.TreffPåEps.AktivtVedtak(
                        brukersSaksnummer = sammendrag2.saksnummer,
                        brukersFnr = sammendrag2.fødselsnummer,
                        identer = personhendelser[1].metadata.personidenter,
                    ),
                ),
            ),
            PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[2].metadata.personidenter,
            ),
            PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[3].metadata.personidenter,
            ),
            PersonhendelseresultatEps.IkkeTreffPåEps(
                identer = personhendelser[4].metadata.personidenter,
            ),
        )
        actual.antallForkastet shouldBe 2
        actual.forkastet shouldBe listOf(
            DryrunResult.DryRunResultPerHendelse(
                resultatBruker = PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                    identer = personhendelser[3].metadata.personidenter,
                ),
                resultatEps = PersonhendelseresultatEps.IkkeTreffPåEps(
                    identer = personhendelser[3].metadata.personidenter,
                ),
            ),
            DryrunResult.DryRunResultPerHendelse(
                resultatBruker = PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                    identer = personhendelser[4].metadata.personidenter,
                ),
                resultatEps = PersonhendelseresultatEps.IkkeTreffPåEps(
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
        )
    }

    @Test
    fun `bostedsadressevurdering fanger også historiske treff, filtrerer vask, og merker tilbake i tid`() {
        val fnrOpprettetKontakt = Fnr.generer()
        val fnrOpprettetKontaktIkkeGjeldende = Fnr.generer()
        val fnrBostedOpprettetHistorisk = Fnr.generer()
        val fnrKorrigertKosmetisk = Fnr.generer()
        val fnrKorrigertReell = Fnr.generer()
        val fnrKorrigertFallback = Fnr.generer()
        val fnrOpphortKontakt = Fnr.generer()
        val fnrAnnullertBosted = Fnr.generer()

        val hendelseIdKontaktOpprett = UUID.randomUUID().toString()
        val hendelseIdKontaktIkkeGjeldende = UUID.randomUUID().toString()
        val hendelseIdBostedHistorisk = UUID.randomUUID().toString()
        val hendelseIdKorrigertKosmetisk = UUID.randomUUID().toString()
        val hendelseIdKorrigertReell = UUID.randomUUID().toString()
        val hendelseIdKorrigertFallback = UUID.randomUUID().toString()
        val hendelseIdOpphort = UUID.randomUUID().toString()
        val hendelseIdAnnullert = UUID.randomUUID().toString()

        val tidligereHendelseIdKosmetisk = UUID.randomUUID().toString()
        val tidligereHendelseIdReell = UUID.randomUUID().toString()
        val tidligereHendelseIdFallback = UUID.randomUUID().toString()

        val kontaktOpprettet = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Kontaktadresse(),
            fnr = fnrOpprettetKontakt,
            hendelseId = hendelseIdKontaktOpprett,
        )
        val kontaktOpprettetIkkeGjeldende = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Kontaktadresse(),
            fnr = fnrOpprettetKontaktIkkeGjeldende,
            hendelseId = hendelseIdKontaktIkkeGjeldende,
        )
        val bostedOpprettetHistorisk = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(
                gyldigFraOgMed = fixedLocalDate.minusDays(10),
            ),
            fnr = fnrBostedOpprettetHistorisk,
            hendelseId = hendelseIdBostedHistorisk,
        )
        val bostedKorrigertKosmetisk = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.KORRIGERT,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnrKorrigertKosmetisk,
            hendelseId = hendelseIdKorrigertKosmetisk,
            tidligereHendelseId = tidligereHendelseIdKosmetisk,
        )
        val bostedKorrigertReell = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.KORRIGERT,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnrKorrigertReell,
            hendelseId = hendelseIdKorrigertReell,
            tidligereHendelseId = tidligereHendelseIdReell,
        )
        val bostedKorrigertFallback = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.KORRIGERT,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnrKorrigertFallback,
            hendelseId = hendelseIdKorrigertFallback,
            tidligereHendelseId = tidligereHendelseIdFallback,
        )
        val kontaktOpphort = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPHØRT,
            hendelse = Personhendelse.Hendelse.Kontaktadresse(),
            fnr = fnrOpphortKontakt,
            hendelseId = hendelseIdOpphort,
        )
        val bostedAnnullert = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.ANNULLERT,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnrAnnullertBosted,
            hendelseId = hendelseIdAnnullert,
        )
        val ikkeVurderte = listOf(
            kontaktOpprettet,
            kontaktOpprettetIkkeGjeldende,
            bostedOpprettetHistorisk,
            bostedKorrigertKosmetisk,
            bostedKorrigertReell,
            bostedKorrigertFallback,
            kontaktOpphort,
            bostedAnnullert,
        )

        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenPdlVurdering() } doReturn ikkeVurderte
            on { hentPersonhendelserKlareForOppgave() } doReturn emptyList()
        }
        val sakRepoMock = mock<SakRepo>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val vedtakServiceMock = mock<VedtakService>()
        val adresseopplysningerPerFnr = mapOf(
            fnrOpprettetKontakt to adresseopplysninger(
                bosted = emptyList(),
            ).right(),
            fnrOpprettetKontaktIkkeGjeldende to adresseopplysninger(
                bosted = emptyList(),
            ).right(),
            fnrBostedOpprettetHistorisk to adresseopplysninger(
                bosted = listOf(
                    Adresseforekomst(
                        historisk = true,
                        hendelseIder = listOf(hendelseIdBostedHistorisk),
                        gateadresse = "Gamlegate 4",
                        postnummer = "0123",
                        folkeregistermetadata = AdresseopplysningerMedMetadata.Folkeregistermetadata(
                            ajourholdstidspunkt = "2026-02-28T10:15:30",
                            gyldighetstidspunkt = "2026-02-10T00:00:00",
                            opphoerstidspunkt = null,
                            kilde = "Matrikkelen",
                            aarsak = "Adresseoppdatering",
                            sekvens = 42,
                        ),
                    ),
                    Adresseforekomst(
                        historisk = false,
                        hendelseIder = listOf(UUID.randomUUID().toString()),
                        gateadresse = "Någate 5",
                        postnummer = "0456",
                    ),
                ),
            ).right(),
            fnrKorrigertKosmetisk to adresseopplysninger(
                bosted = listOf(
                    Adresseforekomst(
                        historisk = true,
                        hendelseIder = listOf(tidligereHendelseIdKosmetisk),
                        gateadresse = "SKJULSTADVEGEN   1",
                        postnummer = "1234",
                    ),
                    Adresseforekomst(
                        historisk = false,
                        hendelseIder = listOf(hendelseIdKorrigertKosmetisk),
                        gateadresse = "Skjulstadvegen 1",
                        postnummer = "1234",
                    ),
                ),
            ).right(),
            fnrKorrigertReell to adresseopplysninger(
                bosted = listOf(
                    Adresseforekomst(
                        historisk = true,
                        hendelseIder = listOf(tidligereHendelseIdReell),
                        gateadresse = "Skjulstadvegen 1",
                        postnummer = "1111",
                    ),
                    Adresseforekomst(
                        historisk = false,
                        hendelseIder = listOf(hendelseIdKorrigertReell),
                        gateadresse = "Skjulstadvegen 1",
                        postnummer = "2222",
                    ),
                ),
            ).right(),
            fnrKorrigertFallback to adresseopplysninger(
                bosted = listOf(
                    Adresseforekomst(
                        historisk = false,
                        hendelseIder = listOf(hendelseIdKorrigertFallback),
                        gateadresse = "Nyveien 10",
                        postnummer = "3333",
                    ),
                ),
            ).right(),
            fnrOpphortKontakt to adresseopplysninger(
                bosted = emptyList(),
            ).right(),
            fnrAnnullertBosted to adresseopplysninger(
                bosted = listOf(
                    Adresseforekomst(
                        historisk = false,
                        hendelseIder = listOf(hendelseIdAnnullert),
                        gateadresse = "Annullertveien 1",
                        postnummer = "0500",
                    ),
                ),
            ).right(),
        )

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(any()) } doAnswer {
                    val fnr = it.getArgument<Fnr>(0)
                    adresseopplysningerPerFnr[fnr] ?: KunneIkkeHentePerson.FantIkkePerson.left()
                }
            },
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )

        personhendelseService.opprettOppgaverForPersonhendelser()

        val vurderinger = argumentCaptor<List<PersonhendelseRepo.PdlVurdering>>()
        verify(personhendelseRepoMock).oppdaterPdlVurdering(vurderinger.capture())
        val relevanteById = vurderinger.firstValue.associate { it.id to it.relevant }

        relevanteById[kontaktOpprettet.id] shouldBe true
        relevanteById[kontaktOpprettetIkkeGjeldende.id] shouldBe true
        relevanteById[bostedOpprettetHistorisk.id] shouldBe true
        relevanteById[bostedKorrigertKosmetisk.id] shouldBe false
        relevanteById[bostedKorrigertReell.id] shouldBe true
        relevanteById[bostedKorrigertFallback.id] shouldBe false
        relevanteById[kontaktOpphort.id] shouldBe true
        relevanteById[bostedAnnullert.id] shouldBe false

        val historiskBostedVurdering = vurderinger.firstValue.single { it.id == bostedOpprettetHistorisk.id }
        val historiskBostedDiff = historiskBostedVurdering.pdlDiff ?: error("Mangler pdlDiff for historisk bostedsvurdering")
        historiskBostedDiff shouldContain "\"hendelseIdFunnetIPdl\":true"
        historiskBostedDiff shouldContain "\"korrelertPåGjeldendeForekomst\":false"
        historiskBostedDiff shouldContain "\"korrelertPåHistoriskForekomst\":true"
        historiskBostedDiff shouldContain "\"pdlTreffAdresse\":\"Gamlegate 4, 0123\""
        historiskBostedDiff shouldContain "\"pdlTreffFolkeregistermetadata\""
        historiskBostedDiff shouldContain "\"kilde\":\"Matrikkelen\""
        historiskBostedDiff shouldContain "\"aarsak\":\"Adresseoppdatering\""

        val annullertBostedVurdering = vurderinger.firstValue.single { it.id == bostedAnnullert.id }
        val annullertBostedDiff = annullertBostedVurdering.pdlDiff ?: error("Mangler pdlDiff for annullert bostedsvurdering")
        annullertBostedDiff shouldContain "\"hendelseIdFunnetIPdl\":true"

        verify(personhendelseRepoMock).hentPersonhendelserUtenPdlVurdering()
        verify(personhendelseRepoMock).hentPersonhendelserKlareForOppgave()
        verifyNoMoreInteractions(
            personhendelseRepoMock,
            sakRepoMock,
            oppgaveServiceMock,
            vedtakServiceMock,
        )
    }

    @Test
    fun `bostedsadresse med PDL-feil blir stående uvurdert for retry`() {
        val fnr = Fnr.generer()
        val bostedOpprettet = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnr,
        )

        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenPdlVurdering() } doReturn listOf(bostedOpprettet)
            on { hentPersonhendelserKlareForOppgave() } doReturn emptyList()
        }
        val personOppslag = mock<PersonOppslag> {
            on { bostedsadresseMedMetadataForSystembruker(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
        }
        val sakRepoMock = mock<SakRepo>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val vedtakServiceMock = mock<VedtakService>()

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = personOppslag,
            vedtakService = vedtakServiceMock,
            oppgaveServiceImpl = oppgaveServiceMock,
            clock = fixedClock,
        )

        personhendelseService.opprettOppgaverForPersonhendelser()

        verify(personhendelseRepoMock).hentPersonhendelserUtenPdlVurdering()
        verify(personhendelseRepoMock).hentPersonhendelserKlareForOppgave()
        verify(personhendelseRepoMock, times(0)).oppdaterPdlVurdering(any())
        verify(personOppslag).bostedsadresseMedMetadataForSystembruker(fnr)
        verifyNoMoreInteractions(
            personhendelseRepoMock,
            personOppslag,
            sakRepoMock,
            oppgaveServiceMock,
            vedtakServiceMock,
        )
    }

    @Test
    fun `OPPHØRT er kun relevant for gjeldende eller historisk etter 2020`() {
        val fnrOpphortGjeldende = Fnr.generer()
        val fnrOpphortHistoriskEtter2020 = Fnr.generer()
        val fnrOpphortHistoriskFoerEllerLik2020 = Fnr.generer()

        val hendelseIdOpphortGjeldende = UUID.randomUUID().toString()
        val hendelseIdOpphortHistoriskEtter2020 = UUID.randomUUID().toString()
        val hendelseIdOpphortHistoriskFoerEllerLik2020 = UUID.randomUUID().toString()

        val opphortGjeldende = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPHØRT,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnrOpphortGjeldende,
            hendelseId = hendelseIdOpphortGjeldende,
        )
        val opphortHistoriskEtter2020 = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPHØRT,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnrOpphortHistoriskEtter2020,
            hendelseId = hendelseIdOpphortHistoriskEtter2020,
        )
        val opphortHistoriskFoerEllerLik2020 = lagAdressePersonhendelseTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPHØRT,
            hendelse = Personhendelse.Hendelse.Bostedsadresse(),
            fnr = fnrOpphortHistoriskFoerEllerLik2020,
            hendelseId = hendelseIdOpphortHistoriskFoerEllerLik2020,
        )

        val personhendelseRepoMock = mock<PersonhendelseRepo> {
            on { hentPersonhendelserUtenPdlVurdering() } doReturn listOf(
                opphortGjeldende,
                opphortHistoriskEtter2020,
                opphortHistoriskFoerEllerLik2020,
            )
            on { hentPersonhendelserKlareForOppgave() } doReturn emptyList()
        }

        val personhendelseService = PersonhendelseServiceImpl(
            sakRepo = mock(),
            personhendelseRepo = personhendelseRepoMock,
            personOppslag = mock<PersonOppslag> {
                on { bostedsadresseMedMetadataForSystembruker(fnrOpphortGjeldende) } doReturn adresseopplysninger(
                    bosted = listOf(
                        Adresseforekomst(
                            historisk = false,
                            hendelseIder = listOf(hendelseIdOpphortGjeldende),
                            gateadresse = "Gjeldendeveien 1",
                            postnummer = "0001",
                        ),
                    ),
                ).right()

                on { bostedsadresseMedMetadataForSystembruker(fnrOpphortHistoriskEtter2020) } doReturn adresseopplysninger(
                    bosted = listOf(
                        Adresseforekomst(
                            historisk = true,
                            hendelseIder = listOf(hendelseIdOpphortHistoriskEtter2020),
                            gateadresse = "Historiskveien 2",
                            postnummer = "0002",
                            folkeregistermetadata = AdresseopplysningerMedMetadata.Folkeregistermetadata(
                                ajourholdstidspunkt = "2021-01-01T00:00:00",
                                gyldighetstidspunkt = "2021-01-01T00:00:00",
                                opphoerstidspunkt = "2021-12-31T00:00:00",
                                kilde = "Dolly",
                                aarsak = null,
                                sekvens = null,
                            ),
                        ),
                    ),
                ).right()

                on { bostedsadresseMedMetadataForSystembruker(fnrOpphortHistoriskFoerEllerLik2020) } doReturn adresseopplysninger(
                    bosted = listOf(
                        Adresseforekomst(
                            historisk = true,
                            hendelseIder = listOf(hendelseIdOpphortHistoriskFoerEllerLik2020),
                            gateadresse = "Gammelveien 3",
                            postnummer = "0003",
                            folkeregistermetadata = AdresseopplysningerMedMetadata.Folkeregistermetadata(
                                ajourholdstidspunkt = "2020-01-01T00:00:00",
                                gyldighetstidspunkt = "2020-01-01T00:00:00",
                                opphoerstidspunkt = "2020-12-31T00:00:00",
                                kilde = "Dolly",
                                aarsak = null,
                                sekvens = null,
                            ),
                        ),
                    ),
                ).right()
            },
            vedtakService = mock(),
            oppgaveServiceImpl = mock(),
            clock = fixedClock,
        )

        personhendelseService.opprettOppgaverForPersonhendelser()

        val vurderinger = argumentCaptor<List<PersonhendelseRepo.PdlVurdering>>()
        verify(personhendelseRepoMock).oppdaterPdlVurdering(vurderinger.capture())

        val vurderingPerId = vurderinger.firstValue.associateBy { it.id }
        vurderingPerId[opphortGjeldende.id]?.relevant shouldBe true
        vurderingPerId[opphortHistoriskEtter2020.id]?.relevant shouldBe true
        vurderingPerId[opphortHistoriskFoerEllerLik2020.id]?.relevant shouldBe false
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
        hendelse: Personhendelse.Hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
    ) =
        Personhendelse.TilknyttetSak.IkkeSendtTilOppgave(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = hendelse,
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

    private fun lagAdressePersonhendelseTilknyttetSak(
        endringstype: Personhendelse.Endringstype,
        hendelse: Personhendelse.Hendelse,
        fnr: Fnr,
        hendelseId: String = UUID.randomUUID().toString(),
        tidligereHendelseId: String? = null,
    ): Personhendelse.TilknyttetSak.IkkeSendtTilOppgave {
        return Personhendelse.TilknyttetSak.IkkeSendtTilOppgave(
            endringstype = endringstype,
            hendelse = hendelse,
            id = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
            sakId = UUID.randomUUID(),
            metadata = Personhendelse.Metadata(
                hendelseId = hendelseId,
                tidligereHendelseId = tidligereHendelseId,
                offset = 0,
                partisjon = 0,
                master = "PDL",
                key = "key",
                personidenter = listOf(fnr.toString(), "1234567890123").toNonEmptyList(),
                eksternOpprettet = null,
            ),
            antallFeiledeForsøk = 0,
            opprettet = fixedTidspunkt,
            gjelderEps = false,
        )
    }

    private fun adresseopplysninger(
        bosted: List<Adresseforekomst>,
    ): AdresseopplysningerMedMetadata {
        return AdresseopplysningerMedMetadata(
            bostedsadresser = bosted.map { it.toDomain() },
        )
    }

    private data class Adresseforekomst(
        val historisk: Boolean,
        val hendelseIder: List<String>,
        val gateadresse: String?,
        val postnummer: String?,
        val folkeregistermetadata: AdresseopplysningerMedMetadata.Folkeregistermetadata? = null,
    ) {
        fun toDomain(): AdresseopplysningerMedMetadata.Adresseopplysning {
            return AdresseopplysningerMedMetadata.Adresseopplysning(
                historisk = historisk,
                hendelseIder = hendelseIder,
                gateadresse = gateadresse,
                postnummer = postnummer,
                folkeregistermetadata = folkeregistermetadata,
            )
        }
    }
}
