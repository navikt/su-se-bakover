package no.nav.su.se.bakover.web.komponenttest.kontrollsamtale

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.jobcontext.NameAndLocalDateId
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KontrollnotatMottattJournalpost
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkKontrollnotatMottatt
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleJobRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleContext.Companion.MAX_RETRIES
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence.KontrollsamtaleJobPostgresRepo
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.komponenttest.withKomptestApplication
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class AutomatiskProsesseringAvKontrollsamtalerMedUtløptFristTest {

    @Test
    fun `automatisk prosessering av kontollsamtaler med utløpt frist`() {
        val tikkendeKlokke = TikkendeKlokke()
        val stønadStart = 1.januar(2021)
        val stønadSlutt = 31.desember(2021)

        val statistikkCaptor = argumentCaptor<String>()
        val kafkaPublisherMock = mock<KafkaPublisher> {
            doNothing().whenever(it).publiser(any(), statistikkCaptor.capture())
        }

        val antallKjøringer = 4

        val k0 = KontrollsamtaleTestData(
            // Møtt til kontrollsamtale, ingen feil, blir prosessert uten stans første kjøring.
            antallKjøringer = antallKjøringer,
            stønadStart = stønadStart,
            stønadSlutt = stønadSlutt,
            møtt = true,
            saksnummer = Saksnummer(2021),
        )
        val k1 = KontrollsamtaleTestData(
            // Møtt til kontrollsamtale, feil i første kjøring, blir prosessert uten stans andre kjøring.
            antallKjøringer = antallKjøringer,
            stønadStart = stønadStart,
            stønadSlutt = stønadSlutt,
            feilFørsteKjøring = setOf(Feil.Journalpost),
            møtt = true,
            saksnummer = Saksnummer(2022),
        )
        val k2 = KontrollsamtaleTestData(
            // Møtt til kontrollsamtale, feiler inntil vi ikke prøver mer.
            antallKjøringer = antallKjøringer,
            stønadStart = stønadStart,
            stønadSlutt = stønadSlutt,
            feilFørsteKjøring = setOf(Feil.Journalpost),
            // Forventer at det lages en oppgave her
            feilAndreKjøring = setOf(Feil.Journalpost),
            møtt = true,
            saksnummer = Saksnummer(2023),
        )
        val k3 = KontrollsamtaleTestData(
            // Ikke møtt til kontrollsamtale, ingen feil, blir prosessert og stanset første kjøring.
            antallKjøringer = antallKjøringer,
            stønadStart = stønadStart,
            stønadSlutt = stønadSlutt,
            møtt = false,
            saksnummer = Saksnummer(2024),
        )
        val k4 = KontrollsamtaleTestData(
            // Ikke møtt til kontrollsamtale, utbetalingsfeil første kjøring, blir prosessert og stanset andre kjøring.
            antallKjøringer = antallKjøringer,
            stønadStart = stønadStart,
            stønadSlutt = stønadSlutt,
            feilFørsteKjøring = setOf(Feil.Utbetaling),
            møtt = false,
            saksnummer = Saksnummer(2025),
        )
        val k5 = KontrollsamtaleTestData(
            // Ikke møtt til kontrollsamtale, feiler også på oppgave, feiler inntil vi ikke prøver mer.
            antallKjøringer = antallKjøringer,
            stønadStart = stønadStart,
            stønadSlutt = stønadSlutt,
            feilFørsteKjøring = setOf(Feil.Utbetaling),
            // Forventer at det prøves å lage en oppgave her
            feilAndreKjøring = setOf(Feil.Utbetaling, Feil.Oppgave),
            møtt = false,
            saksnummer = Saksnummer(2026),
        )
        val mockData = MockData(listOf(k0, k1, k2, k3, k4, k5))
        withKomptestApplication(
            clock = tikkendeKlokke,
            clientsBuilder = { databaseRepos, clock ->
                testClientBuilder(
                    mockData = mockData,
                    clock = clock,
                    databaseRepos = databaseRepos,
                    kafkaPublisher = kafkaPublisherMock,
                )
            },
        ) { appComponents ->
            val kontrollsamtaleService =
                appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService as KontrollsamtaleServiceImpl
            val jobContextPostgresRepo = JobContextPostgresRepo(
                sessionFactory = appComponents.databaseRepos.sessionFactory as PostgresSessionFactory,
            )
            val kontrollsamtaleJobRepo = KontrollsamtaleJobPostgresRepo(
                repo = jobContextPostgresRepo,
            )
            val utløptFristForKontrollsamtaleService =
                appComponents.services.kontrollsamtaleSetup.utløptFristForKontrollsamtaleService
            val testController = TestController(
                mockData = mockData,
                kontrollsamtaler = mockData.kontrollsamtaler.map {
                    it.opprettSakMedKontrollsamtale(
                        client,
                        kontrollsamtaleService,
                    )
                },
                antallKjøringer = antallKjøringer,
                kontrollsamtaleService = kontrollsamtaleService,
                tikkendeKlokke = tikkendeKlokke,
                kontrollsamtaleJobRepo = kontrollsamtaleJobRepo,
            )

            (0 until antallKjøringer).forEach { kjøring ->
                val utløpsfristKontrollsamtale = testController.utløpsfrist
                utløptFristForKontrollsamtaleService.håndterUtløpsdato(utløpsfristKontrollsamtale)
                kontrollsamtaleJobRepo.hent(
                    id = NameAndLocalDateId(
                        name = "HåndterUtløptFristForKontrollsamtale",
                        date = utløpsfristKontrollsamtale,
                    ),
                )!!.also {
                    withClue("kjøring: $kjøring, context: $it") {
                        it.id() shouldBe NameAndLocalDateId(
                            name = "HåndterUtløptFristForKontrollsamtale",
                            date = utløpsfristKontrollsamtale,
                        )
                        it.prosessert().toList().sorted() shouldBe testController.kontrollsamtaler
                            .filter { it.erIkkeFeil(kjøring) }
                            .map { it.kontrollsamtale.id }
                            .sorted()

                        it.ikkeMøtt().toList().sorted() shouldBe testController.kontrollsamtaler
                            .filter { it.erIkkeFeil(kjøring) && !it.data.møtt }
                            .map { it.kontrollsamtale.id }
                            .sorted()

                        it.feilet().toList().sortedBy { it.id } shouldBe testController.kontrollsamtaler
                            .filter { it.erFeil(kjøring) }
                            .map {
                                UtløptFristForKontrollsamtaleContext.Feilet(
                                    id = it.kontrollsamtale.id,
                                    retries = it.retries(kjøring),
                                    feil = it.forventetFeilmelding(kjøring),
                                    oppgaveId = it.oppgaveId(kjøring),
                                )
                            }
                            .sortedBy { it.id }
                    }
                }
                assertUendret(
                    saker = testController.kontrollsamtaler.filter { it.erFeil(kjøring) }.map { it.sakId },
                    periode = Periode.create(stønadStart, stønadSlutt),
                    sakService = appComponents.services.sak,
                    kontrollsamtaleService = appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService as KontrollsamtaleServiceImpl,
                )
                assertMøttTilSamtale(
                    saker = testController.kontrollsamtaler.filterNot { it.erFeil(kjøring) }.filter { it.data.møtt }.map { it.sakId },
                    periode = Periode.create(stønadStart, stønadSlutt),
                    sakService = appComponents.services.sak,
                    kontrollsamtaleService = appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService as KontrollsamtaleServiceImpl,
                )
                assertIkkeMøttTilSamtale(
                    saker = testController.kontrollsamtaler.filterNot { it.erFeil(kjøring) }.filterNot { it.data.møtt }.map { it.sakId },
                    periode = Periode.create(utløpsfristKontrollsamtale.førsteINesteMåned(), stønadSlutt),
                    sakService = appComponents.services.sak,
                    kontrollsamtaleService = appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService as KontrollsamtaleServiceImpl,
                )
                testController.nesteKjøring()
            }
            statistikkCaptor.allValues.filter { it.contains(""""behandlingStatus":"REGISTRERT"""") && it.contains(""""resultatBegrunnelse":"MANGLENDE_KONTROLLERKLÆRING"""") } shouldHaveSize 3
            statistikkCaptor.allValues.filter { it.contains(""""behandlingStatus":"IVERKSATT"""") && it.contains(""""resultatBegrunnelse":"MANGLENDE_KONTROLLERKLÆRING"""") } shouldHaveSize 3
            statistikkCaptor.allValues.filter { it.contains(""""vedtaksresultat":"STANSET"""") } shouldHaveSize 3
        }
    }

    private data class TestController(
        val mockData: MockData,
        val kontrollsamtaler: List<KontrollsamtaleTestDataMedSak>,
        val antallKjøringer: Int,
        private val kontrollsamtaleService: KontrollsamtaleService,
        private val tikkendeKlokke: TikkendeKlokke,
        private val kontrollsamtaleJobRepo: KontrollsamtaleJobRepo,
    ) {
        init {
            tikkendeKlokke.spolTil(kontrollsamtaler.first().kontrollsamtale.innkallingsdato)
            kontrollsamtaler.forEach {
                kontrollsamtaleService.kallInn(
                    sakId = it.sakId,
                    kontrollsamtale = it.kontrollsamtale,
                )
            }
        }

        val utløpsfrist = kontrollsamtaler.first().kontrollsamtale.frist

        fun nesteKjøring() {
            mockData.nesteKjøring()
        }
    }

    /**
     * Må sendes inn i applikasjonsbuilderen, så den kan ikke inneholde data generert av applikasjonen.
     * Husk å kall nesteKjøring() for hvert kall til hentInnkalteKontrollsamtalerMedFristUtløpt(...)
     */
    private data class MockData(
        val kontrollsamtaler: List<KontrollsamtaleTestData>,
    ) {
        var nåværendeKjøring = 0

        fun nesteKjøring() {
            nåværendeKjøring += 1
        }
    }

    private data class KontrollsamtaleTestDataMedSak(
        val data: KontrollsamtaleTestData,
        private val client: HttpClient,
        private val kontrollsamtaleService: KontrollsamtaleServiceImpl,
    ) {
        fun erFeil(kjøring: Int) = data.feil(kjøring).isNotEmpty()
        fun erIkkeFeil(kjøring: Int) = !erFeil(kjøring)

        fun retries(kjøring: Int): Int {
            return minOf(kjøring, MAX_RETRIES)
        }

        fun oppgaveId(kjøring: Int): String? {
            if (retries(kjøring) < MAX_RETRIES) return null
            // Vi skal lage en oppgave dersom vi ikke har flere retries
            return data.feil(kjøring).mapNotNull {
                when (it) {
                    Feil.Oppgave -> null
                    Feil.Utbetaling, Feil.Journalpost -> data.oppgaveId
                }
            }.singleOrNull()
        }

        fun forventetFeilmelding(kjøring: Int): String {
            val feil = data.feil(kjøring)
            // Denne er litt shaky. Bør kanskje spesifiseres som en constructor-parameter
            return feil.first().forventetFeilmelding
        }

        val sakId = innvilgSøknad(
            fraOgMed = data.stønadStart,
            tilOgMed = data.stønadSlutt,
            client = client,
            fnr = data.fnr.toString(),
        )
        val kontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).single()

        init {
            if (data.opprettRevurdering) {
                opprettRevurdering(
                    sakId = sakId.toString(),
                    fraOgMed = data.stønadStart.toString(),
                    tilOgMed = data.stønadSlutt.toString(),
                    client = client,
                )
            }
        }
    }

    /**
     * Representerer en sak med en kontrollsamtale
     */
    private data class KontrollsamtaleTestData(
        val antallKjøringer: Int,
        val stønadStart: LocalDate,
        val stønadSlutt: LocalDate,
        val møtt: Boolean,
        // Siden vi kun vil prøve 2 ganger, gjør vi dette enkelt.
        val feilFørsteKjøring: Set<Feil> = emptySet(),
        val feilAndreKjøring: Set<Feil> = emptySet(),
        val opprettRevurdering: Boolean = false,
        val saksnummer: Saksnummer,
        val oppgaveId: String = "oppgave-som-lages-ved-feil",
    ) {
        init {
            if (møtt) {
                require((feilFørsteKjøring + feilAndreKjøring).all { it == Feil.Journalpost }) {
                    "Dersom en person har møtt, vil vi ikke sende utbetalingslinjer eller lage oppgaver."
                }
            }
            // Testen tar ikke høyde for at denne verdien kan endre seg. Det burde vært mulig å konfigurere den fra testen.
            require(MAX_RETRIES <= 2)
        }

        val fnr = Fnr.generer()

        fun opprettSakMedKontrollsamtale(
            client: HttpClient,
            kontrollsamtaleService: KontrollsamtaleServiceImpl,
        ): KontrollsamtaleTestDataMedSak {
            return KontrollsamtaleTestDataMedSak(
                data = this,
                client = client,
                kontrollsamtaleService = kontrollsamtaleService,
            )
        }

        fun feil(kjøring: Int): Set<Feil> = when (kjøring) {
            0 -> feilFørsteKjøring
            1 -> feilAndreKjøring
            else -> emptySet()
        }

        fun erUtbetalingsfeil(kjøring: Int) = feil(kjøring).contains(Feil.Utbetaling)
        fun erOppgavefeil(kjøring: Int) = feil(kjøring).contains(Feil.Oppgave)
        fun erJournalpostfeil(kjøring: Int) = feil(kjøring).contains(Feil.Journalpost)
    }

    companion object {
        fun innvilgSøknad(
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            client: HttpClient,
            fnr: String = Fnr.generer().toString(),
        ): UUID {
            return opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = fraOgMed.toString(),
                tilOgMed = tilOgMed.toString(),
                client = client,
            ).let {
                hentSak(BehandlingJson.hentSakId(it), client = client).let { sakJson ->
                    UUID.fromString(hentSakId(sakJson))
                }
            }
        }
    }

    private fun testClientBuilder(
        mockData: MockData,
        clock: Clock,
        databaseRepos: DatabaseRepos,
        kafkaPublisher: KafkaPublisher,
    ): Clients {
        return TestClientsBuilder(
            clock = clock,
            databaseRepos = databaseRepos,
        ).build(applicationConfig()).let { clients ->
            clients.copy(
                utbetalingPublisher = object : UtbetalingPublisher by clients.utbetalingPublisher {
                    override fun publishRequest(
                        utbetalingsrequest: Utbetalingsrequest,
                    ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
                        if (!utbetalingsrequest.value.contains("HVIL")) {
                            return utbetalingsrequest.right()
                        }
                        mockData.kontrollsamtaler
                            .first { utbetalingsrequest.value.contains(it.fnr.toString()) }.let {
                                return if (it.erUtbetalingsfeil(mockData.nåværendeKjøring)) {
                                    UtbetalingPublisher.KunneIkkeSendeUtbetaling(utbetalingsrequest).left()
                                } else {
                                    utbetalingsrequest.right()
                                }
                            }
                    }
                },
                journalpostClient = object : JournalpostClient by clients.journalpostClient {
                    override fun kontrollnotatMotatt(
                        saksnummer: Saksnummer,
                        periode: DatoIntervall,
                    ): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt> {
                        val kontrollsamtale = mockData.kontrollsamtaler
                            .first { saksnummer == it.saksnummer }
                        if (kontrollsamtale.erJournalpostfeil(mockData.nåværendeKjøring)) {
                            return KunneIkkeSjekkKontrollnotatMottatt("Generert av mock: KunneIkkeSjekkKontrollnotatMottatt").left()
                        }
                        kontrollsamtale.let {
                            return if (it.møtt) {
                                ErKontrollNotatMottatt.Ja(journalpostKontrollnotat(JournalpostId("1111"))).right()
                            } else {
                                ErKontrollNotatMottatt.Nei.right()
                            }
                        }
                    }
                },
                kafkaPublisher = kafkaPublisher,
                oppgaveClient = object : OppgaveClient by clients.oppgaveClient {
                    override fun opprettOppgaveMedSystembruker(
                        config: OppgaveConfig,
                    ): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> {
                        val underTest =
                            mockData.kontrollsamtaler.first { config.saksreferanse == it.saksnummer.toString() }
                        return if (config !is OppgaveConfig.Kontrollsamtale) {
                            OppgaveId(underTest.oppgaveId).right()
                        } else {
                            if (underTest.erOppgavefeil(mockData.nåværendeKjøring)) {
                                OppgaveFeil.KunneIkkeOppretteOppgave.left()
                            } else {
                                OppgaveId(
                                    underTest.oppgaveId,
                                ).right()
                            }
                        }
                    }
                },
            )
        }
    }

    private enum class Feil(val forventetFeilmelding: String) {
        Oppgave("FIXME"),
        Utbetaling("class no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet${"\$"}Protokollfeil"),
        Journalpost("class no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkKontrollnotatMottatt"),
        ;
    }

    private fun journalpostKontrollnotat(id: JournalpostId): KontrollnotatMottattJournalpost {
        return KontrollnotatMottattJournalpost(
            tema = JournalpostTema.SUP,
            journalstatus = JournalpostStatus.JOURNALFOERT,
            journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
            saksnummer = saksnummer,
            tittel = "NAV SU Kontrollnotat",
            datoOpprettet = LocalDate.now(),
            journalpostId = id,
        )
    }

    private fun assertIkkeMøttTilSamtale(
        saker: List<UUID>,
        periode: Periode,
        sakService: SakService,
        kontrollsamtaleService: KontrollsamtaleServiceImpl,
    ) {
        saker.forEach { sakId ->
            sakService.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger.single().shouldBeType<StansAvYtelseRevurdering.IverksattStansAvYtelse>()
                sak.vedtakListe.single { it is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse }
                sak.vedtakstidslinje().also { vedtakstidslinje ->
                    periode.måneder().map {
                        vedtakstidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak to sak.utbetalingstidslinje(it)
                            .gjeldendeForDato(it.fraOgMed)
                    }.forEach { (vedtak, utbetaling) ->
                        (vedtak is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse && utbetaling is UtbetalingslinjePåTidslinje.Stans) shouldBe true
                    }
                }
                kontrollsamtaleService.hentForSak(sak.id)
                    .single { it.status == Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST }.let {
                        it.journalpostIdKontrollnotat shouldBe beNull()
                    }
            }
        }
    }

    private fun assertMøttTilSamtale(
        saker: List<UUID>,
        periode: Periode,
        sakService: SakService,
        kontrollsamtaleService: KontrollsamtaleServiceImpl,
    ) {
        saker.forEach { sakId ->
            sakService.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger shouldBe emptyList()
                sak.vedtakListe.single()
                sak.vedtakstidslinje().also { vedtakstidslinje ->
                    periode.måneder().map {
                        vedtakstidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak to sak.utbetalingstidslinje(it)
                            .gjeldendeForDato(it.fraOgMed)
                    }.forEach { (vedtak, utbetaling) ->
                        (vedtak is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling && utbetaling is UtbetalingslinjePåTidslinje.Ny) shouldBe true
                    }
                }
                kontrollsamtaleService.hentForSak(sak.id).single { it.status == Kontrollsamtalestatus.GJENNOMFØRT }
                    .let {
                        it.journalpostIdKontrollnotat shouldNot beNull()
                    }
            }
        }
    }

    private fun assertUendret(
        saker: List<UUID>,
        periode: Periode,
        sakService: SakService,
        kontrollsamtaleService: KontrollsamtaleServiceImpl,
    ) {
        saker.forEach { sakId ->
            sakService.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger shouldBe emptyList()
                sak.vedtakListe.single()
                sak.vedtakstidslinje().let { tidslinje ->
                    periode.måneder().map {
                        tidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak to sak.utbetalingstidslinje(it)
                            .gjeldendeForDato(it.fraOgMed)
                    }.forEach { (vedtak, utbetaling) ->
                        (vedtak is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling && utbetaling is UtbetalingslinjePåTidslinje.Ny) shouldBe true
                    }
                }
                kontrollsamtaleService.hentForSak(sak.id).forEach {
                    ((it.status == Kontrollsamtalestatus.INNKALT || it.status == Kontrollsamtalestatus.PLANLAGT_INNKALLING) && it.journalpostIdKontrollnotat == null) shouldBe true
                }
            }
        }
    }
}
