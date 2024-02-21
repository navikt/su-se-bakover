package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.Dokumenttilstand
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.brev.Brevvalg
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avvisSøknadMedBrev
import no.nav.su.se.bakover.test.avvisSøknadUtenBrev
import no.nav.su.se.bakover.test.bortfallSøknad
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonAnnet
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySakMedJournalførtSøknadUtenOppgave
import no.nav.su.se.bakover.test.søknad.nySakMedLukketSøknad
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTrukket
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class LukkSøknadServiceImpl_lukkSøknadOgSøknadsbehandlingTest {

    @Test
    fun `fant ikke søknad`() {
        val søknadId = UUID.randomUUID()
        ServiceOgMocks(
            lukkSøknadCommand = trekkSøknad(søknadId),
            sakService = mock {
                on { hentSakForSøknad(any()) } doReturn FantIkkeSak.left()
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataInformasjonAnnet(tittel = "test-dokument-informasjon-annet").right()
            },
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Fant ikke sak for søknadId $søknadId"

            serviceAndMocks.verifyHentSakForSøknad(søknadId)
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan ikke sette lukketDato tidligere enn da søknaden var opprettet`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave(
            clock = fixedClockAt(1.februar(2021)),
        )

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(
                søknadId = søknad.id,
                lukketTidspunkt = 1.februar(2021).startOfDay(),
                trukketDato = 20.januar(2021),
            ),

        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "trukketDato 2021-01-20 må være samtidig eller etter mottaksdato 2021-02-01 for søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `søknadsbehandling til attestering skal ikke bli lukket`() {
        val (sak, søknadsbehandling) = søknadsbehandlingTilAttesteringInnvilget()
        val søknad = sak.søknader.first()

        ServiceOgMocks(
            sak = sak,
            søknadsbehandling = søknadsbehandling,
            søknad = søknad,
            lukkSøknadCommand = bortfallSøknad(
                søknadId = søknad.id,
            ),

        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad ${søknad.id} og søknadsbehandling. Underliggende feil: KanIkkeLukkeEnSøknadsbehandlingTilAttestering"
            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `iverksatt søknadsbehandling skal ikke bli lukket`() {
        val (sak, søknadsbehandling) = søknadsbehandlingIverksattInnvilget()
        val søknad = sak.søknader.first()

        ServiceOgMocks(
            sak = sak,
            søknadsbehandling = søknadsbehandling,
            søknad = søknad,
            lukkSøknadCommand = bortfallSøknad(
                søknadId = søknad.id,
            ),

        ).let { serviceAndMocks ->
            shouldThrow<IllegalStateException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Fant ingen, en/flere ikke åpen behandling, eller flere åpne søknadsbehandlinger for søknad ${søknad.id}. Antall behandlinger funnet 1"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en søknad med lukket søknadsbehandling skal ikke kunne bli lukket igjen`() {
        val (sak, søknadsbehandling) = søknadsbehandlingTrukket()
        val søknad = sak.søknader.first() as Søknad.Journalført.MedOppgave

        ServiceOgMocks(
            sak = sak,
            søknadsbehandling = søknadsbehandling,
            søknad = søknad,
            lukkSøknadCommand = bortfallSøknad(
                søknadId = søknad.id,
            ),

        ).let { serviceAndMocks ->
            shouldThrow<IllegalStateException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Fant ingen, en/flere ikke åpen behandling, eller flere åpne søknadsbehandlinger for søknad ${søknad.id}. Antall behandlinger funnet 1"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val søknadId = UUID.randomUUID()
        val lukkSøknadCommand: LukkSøknadCommand = trekkSøknad(
            søknadId = søknadId,
        )
        val (sak, søknad) = nySakMedLukketSøknad(
            søknadId = søknadId,
            lukkSøknadCommand = lukkSøknadCommand,
        )
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = lukkSøknadCommand,

        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad og søknadsbehandling. Søknaden må være i tilstanden: Søknad.Journalført.MedOppgave.IkkeLukket for sak ${sak.id} og søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `skal ikke kunne lukke journalført søknad uten oppgave`() {
        val (sak, søknad) = nySakMedJournalførtSøknadUtenOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),

        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad og søknadsbehandling. Søknaden må være i tilstanden: Søknad.Journalført.MedOppgave.IkkeLukket for sak ${sak.id} og søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `skal ikke kunne lukke søknad som mangler journalpost og oppgave`() {
        val (sak, søknad) = nySakMedNySøknad()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad og søknadsbehandling. Søknaden må være i tilstanden: Søknad.Journalført.MedOppgave.IkkeLukket for sak ${sak.id} og søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Feil ved generering av brev ved lukking`() {
        val (sak, søknadsbehandling) = nySøknadsbehandlingMedStønadsperiode()
        val søknad = sak.søknader.first()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            søknadsbehandling = søknadsbehandling,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
            },
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke konvertere LagBrevRequest til dokument ved lukking av søknad ${søknad.id} og søknadsbehandling. Underliggende grunn: FeilVedGenereringAvPdf"

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSakForSøknad()
                serviceAndMocks.verifyLagBrev()
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `trekker en søknad uten mangler`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        val dokumentUtenMetadata = dokumentUtenMetadataInformasjonAnnet(tittel = "test-dokument-informasjon-annet")
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadata.right()
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll(dokumentUtenMetadata = dokumentUtenMetadata)
        }
    }

    // TODO jah: Slett tilsvarende lukk søknad tester hvis den/de flyttes til regresjonslaget.
    @Test
    fun `lukker avvist søknad uten brev`() {
        val (sak, søknadsbehandling) = nySøknadsbehandlingMedStønadsperiode()
        val søknad = sak.søknader.first() as Søknad.Journalført.MedOppgave.IkkeLukket
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            søknadsbehandling = søknadsbehandling,
            lukkSøknadCommand = avvisSøknadUtenBrev(søknad.id),
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll(
                includeBrev = false,
            )
        }
    }

    @Test
    fun `lukker avvist søknad med brev`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        val dokumentUtenMetadata = dokumentUtenMetadataVedtak()
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = avvisSøknadMedBrev(
                søknadId = søknad.id,
                brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("Fritekst"),
            ),
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadata.right()
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll(dokumentUtenMetadata = dokumentUtenMetadata)
        }
    }

    @Test
    fun `Lukker søknad selvom vi ikke klarte lukke oppgaven`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        val dokumentUtenMetadata = dokumentUtenMetadataInformasjonAnnet(
            tittel = "Bekrefter at søknad er trukket",
        )
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(
                søknadId = søknad.id,
            ),
            oppgaveService = mock {
                on { lukkOppgave(any()) } doAnswer { KunneIkkeLukkeOppgave.FeilVedHentingAvOppgave(it.getArgument(0)).left() }
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadata.right()
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll(
                dokumentUtenMetadata = dokumentUtenMetadata,
            )
        }
    }

    private class ServiceOgMocks(
        val sak: Sak? = null,
        private val søknad: Søknad? = null,
        val søknadsbehandling: Søknadsbehandling? = null,
        private val lukkSøknadCommand: LukkSøknadCommand,
        private val sakService: SakService = mock {
            if (sak != null) {
                on { hentSakForSøknad(any()) } doReturn sak.right()
            }
        },
        private val brevService: BrevService = mock(),
        private val oppgaveService: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
        },
        sessionFactory: SessionFactory = TestSessionFactory(),
        private val lukkSøknadServiceObserver: StatistikkEventObserver = mock(),
    ) {
        init {
            søknad?.also {
                require(sak!!.søknader.contains(søknad))
            }
            søknadsbehandling?.also {
                require(sak!!.søknadsbehandlinger.contains(søknadsbehandling))
            }
        }

        private val søknadService: SøknadService = mock()
        private val søknadsbehandlingService: SøknadsbehandlingService = mock()

        private val lukkSøknadService = LukkSøknadServiceImpl(
            søknadService = søknadService,
            sakService = sakService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            søknadsbehandlingService = søknadsbehandlingService,
            sessionFactory = sessionFactory,
        ).apply { addObserver(lukkSøknadServiceObserver) }

        fun lukkSøknad(): Triple<Søknad.Journalført.MedOppgave.Lukket, LukketSøknadsbehandling?, Fnr> = lukkSøknadService.lukkSøknad(
            lukkSøknadCommand,
        )

        val allMocks = listOf(
            søknadService,
            sakService,
            brevService,
            oppgaveService,
            søknadsbehandlingService,
            lukkSøknadServiceObserver,
        ).toTypedArray()

        fun verifyHentSakForSøknad(søknadId: UUID = søknad!!.id) {
            verify(sakService).hentSakForSøknad(argThat { it shouldBe søknadId })
        }

        fun verifyLagBrev() {
            verify(brevService).lagDokument(
                argThat {
                    it shouldBe when (lukkSøknadCommand) {
                        is LukkSøknadCommand.MedBrev.AvvistSøknad -> AvvistSøknadDokumentCommand(
                            saksnummer = sak!!.saksnummer,
                            brevvalg = lukkSøknadCommand.brevvalg as Brevvalg.SaksbehandlersValg.SkalSendeBrev,
                            fødselsnummer = sak.fnr,
                            saksbehandler = saksbehandler,
                        )

                        is LukkSøknadCommand.MedBrev.TrekkSøknad -> TrukketSøknadDokumentCommand(
                            trukketDato = 1.januar(2021),
                            saksnummer = sak!!.saksnummer,
                            søknadOpprettet = fixedTidspunkt,
                            fødselsnummer = sak.fnr,
                            saksbehandler = saksbehandler,
                        )

                        is LukkSøknadCommand.UtenBrev -> fail("Skal ikke trigge brevService.lagBrev(...) i dette tilfellet.")
                    }
                },
                anyOrNull(),
            )
        }

        fun verifyPersisterLukketSøknad() {
            verify(søknadService).persisterSøknad(
                argThat {
                    it shouldBe expectedLukketSøknad()
                },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
        }

        fun verifyPersisterLukketSøknadsbehandling() {
            verify(søknadsbehandlingService).persisterSøknadsbehandling(
                argThat {
                    it shouldBe expectedLukketSøknadsbehandling()
                },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
        }

        fun verifyAll(
            includeBrev: Boolean = true,
            dokumentUtenMetadata: Dokument.UtenMetadata? = null,
        ) {
            inOrder(*allMocks) {
                verifyHentSakForSøknad()
                if (includeBrev) {
                    verifyLagBrev()
                }
                verifyPersisterLukketSøknad()
                if (søknadsbehandling != null) {
                    verifyPersisterLukketSøknadsbehandling()
                }
                if (includeBrev) {
                    verifyLagreDokument(dokumentUtenMetadata!!)
                }
                verifyLukkOppgave()
                verifyStatistikkhendelse()
            }
            verifyNoMoreInteractions()
        }

        fun expectedSak(): Triple<Søknad.Journalført.MedOppgave.Lukket, LukketSøknadsbehandling?, Fnr> =
            Triple(expectedLukketSøknad(), if (søknadsbehandling != null) expectedLukketSøknadsbehandling() else null, sak!!.fnr)

        fun expectedLukketSøknadsbehandling() = LukketSøknadsbehandling.createFromPersistedState(
            søknadsbehandling = søknadsbehandling!!,
            søknad = expectedLukketSøknad(),
        ).copy(
            søknadsbehandlingsHistorikk = søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                nySøknadsbehandlingshendelse(
                    tidspunkt = fixedTidspunkt,
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.Lukket,

                ),
            ),
        )

        fun expectedLukketSøknad(): Søknad.Journalført.MedOppgave.Lukket {
            return when (lukkSøknadCommand) {
                is LukkSøknadCommand.MedBrev.AvvistSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.Avvist(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        brevvalg = lukkSøknadCommand.brevvalg,
                        innsendtAv = veileder,
                        dokumenttilstand = Dokumenttilstand.IKKE_GENERERT_ENDA,
                    )
                }

                is LukkSøknadCommand.MedBrev.TrekkSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        trukketDato = lukkSøknadCommand.trukketDato,
                        innsendtAv = veileder,
                        dokumenttilstand = Dokumenttilstand.IKKE_GENERERT_ENDA,
                    )
                }

                is LukkSøknadCommand.UtenBrev.AvvistSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.Avvist(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        brevvalg = Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(),
                        innsendtAv = veileder,
                        dokumenttilstand = Dokumenttilstand.SKAL_IKKE_GENERERE,
                    )
                }

                is LukkSøknadCommand.UtenBrev.BortfaltSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.Bortfalt(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        innsendtAv = veileder,
                    )
                }
            }
        }

        fun verifyLagreDokument(
            dokumentUtenMetadata: Dokument.UtenMetadata,
        ) {
            verify(brevService).lagreDokument(
                argThat { dokument ->
                    dokument shouldBe dokumentUtenMetadata.leggTilMetadata(
                        Dokument.Metadata(
                            sakId = sak!!.id,
                            søknadId = søknad!!.id,
                        ),
                    )
                },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
        }

        fun verifyLukkOppgave() {
            verify(oppgaveService).lukkOppgave(argThat { it shouldBe (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).oppgaveId })
        }

        fun verifyStatistikkhendelse() {
            if (søknadsbehandling == null) {
                verify(lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe StatistikkEvent.Søknad.Lukket(
                            søknad = expectedLukketSøknad(),
                            saksnummer = sak!!.saksnummer,
                        )
                    },
                )
            } else {
                verify(lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe StatistikkEvent.Behandling.Søknad.Lukket(
                            søknadsbehandling = expectedLukketSøknadsbehandling(),
                            saksbehandler = saksbehandler,
                        )
                    },
                )
            }
        }

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(*allMocks)
        }
    }
}
