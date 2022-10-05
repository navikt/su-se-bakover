package no.nav.su.se.bakover.web.komponenttest

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.jobcontext.NameAndLocalDateId
import no.nav.su.se.bakover.domain.jobcontext.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KontrollnotatMottattJournalpost
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtaleKomponentTest {

    @Test
    fun `oppretter kontrollsamtale, kall inn og annuller`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()
        val førsteInnkalling = stønadStart.plusMonths(4).startOfMonth()
        val førsteFrist = stønadStart.plusMonths(4).endOfMonth()
        val andreInnkalling = stønadStart.plusMonths(8).startOfMonth()
        val andreFrist = stønadStart.plusMonths(8).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale

            val sakId = innvilgSøknad(
                fraOgMed = stønadStart,
                tilOgMed = stønadSlutt,
            )

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).first()

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale,
            )

            val andrePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).last()

            tikkendeKlokke.spolTil(andrePlanlagteKontrollsamtale.innkallingsdato)

            opprettIverksattRevurdering(
                sakid = sakId.toString(),
                fraogmed = andreInnkalling.toString(),
                tilogmed = stønadSlutt.toString(),
                leggTilUtenlandsoppholdRevurdering = { sakid, behandlingId, fraOgMed, tilOgMed, _ ->
                    leggTilUtenlandsoppholdRevurdering(
                        sakId = sakid,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        vurdering = UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet.toString(),
                    )
                },
            )

            kontrollsamtaleService.hentForSak(sakId)
                .also { kontrollsamtaler ->
                    kontrollsamtaler.first().also {
                        it.innkallingsdato shouldBe førsteInnkalling
                        it.frist shouldBe førsteFrist
                        it.dokumentId shouldNot beNull()
                        it.status shouldBe Kontrollsamtalestatus.INNKALT
                        it.sakId shouldBe sakId
                    }
                    kontrollsamtaler.last().also {
                        it.innkallingsdato shouldBe andreInnkalling
                        it.frist shouldBe andreFrist
                        it.dokumentId shouldBe beNull()
                        it.status shouldBe Kontrollsamtalestatus.ANNULLERT
                        it.sakId shouldBe sakId
                    }
                }
        }
    }

    @Test
    fun `automatisk prosessering av kontollsamtaler med utløpt frist`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()

        val statistikkCaptor = argumentCaptor<String>()
        val kafkaPublisherMock = mock<KafkaPublisher>() {
            doNothing().whenever(it).publiser(any(), statistikkCaptor.capture())
        }
        withKomptestApplication(
            clock = tikkendeKlokke,
            clientsBuilder = { databaseRepos, klokke ->
                TestClientsBuilder(
                    clock = klokke,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).let {
                    it.copy(
                        utbetalingPublisher = object : UtbetalingPublisher by it.utbetalingPublisher {
                            var count = 0
                            override fun publishRequest(utbetalingsrequest: Utbetalingsrequest): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
                                return if (utbetalingsrequest.value.contains("HVIL")) { // feil ved publisering av utbetalinger for stans
                                    count++
                                    if (count in (2..4)) {
                                        UtbetalingPublisher.KunneIkkeSendeUtbetaling(utbetalingsrequest).left()
                                    } else {
                                        utbetalingsrequest.right()
                                    }
                                } else {
                                    utbetalingsrequest.right()
                                }
                            }
                        },
                        journalpostClient = mock {
                            on { kontrollnotatMotatt(any(), any()) } doReturnConsecutively listOf(
                                ErKontrollNotatMottatt.Nei.right(),
                                ErKontrollNotatMottatt.Ja(journalpostKontrollnotat(JournalpostId("1111"))).right(),
                                ErKontrollNotatMottatt.Ja(journalpostKontrollnotat(JournalpostId("2222"))).right(),
                                ErKontrollNotatMottatt.Nei.right(),
                                ErKontrollNotatMottatt.Nei.right(),
                                ErKontrollNotatMottatt.Nei.right(),
                                ErKontrollNotatMottatt.Nei.right(),
                            )
                        },
                        kafkaPublisher = kafkaPublisherMock,
                        oppgaveClient = object : OppgaveClient by it.oppgaveClient {
                            var count = 0
                            override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> {
                                return if (config is OppgaveConfig.KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale) {
                                    count++
                                    if (count == 2) {
                                        OppgaveFeil.KunneIkkeOppretteOppgave.left()
                                    } else {
                                        OppgaveId("stubbed").right()
                                    }
                                } else {
                                    OppgaveId("stubbed").right()
                                }
                            }
                        },
                    )
                }
            },
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale
            val utløptFristForKontrollsamtaleService = appComponents.services.utløptFristForKontrollsamtaleService

            val sakIds = listOf(
                innvilgSøknad( // ikke møtt - iverksatt stans ok
                    fraOgMed = stønadStart,
                    tilOgMed = stønadSlutt,
                ),
                innvilgSøknad( // møtt - oppdater kontrollsamtale med journalpost
                    fraOgMed = stønadStart,
                    tilOgMed = stønadSlutt,
                ),
                innvilgSøknad( // møtt - oppdater kontrollsamtale med journalpost
                    fraOgMed = stønadStart,
                    tilOgMed = stønadSlutt,
                ),
                innvilgSøknad( // ikke møtt - utbetaling feiler ved første kjøring - iverksatt stans ok ved andre kjøring
                    fraOgMed = stønadStart,
                    tilOgMed = stønadSlutt,
                ),
                innvilgSøknad( // ikke møtt - utbetaling feiler ved første og andre og andre kjøring - iverksatt stans ok ved tredje kjøring
                    fraOgMed = stønadStart,
                    tilOgMed = stønadSlutt,
                ),
                innvilgSøknad( // ikke møtt - opprettelse av stans feiler ved alle kjøringer
                    fraOgMed = stønadStart,
                    tilOgMed = stønadSlutt,
                ).also {
                    opprettRevurdering(
                        sakId = it.toString(),
                        fraOgMed = stønadStart.toString(),
                        tilOgMed = stønadSlutt.toString(),
                    )
                },
                innvilgSøknad( // ikke møtt - opprettelse av stans feiler ved alle kjøringer, opprettelse av oppgave feiler
                    fraOgMed = stønadStart,
                    tilOgMed = stønadSlutt,
                ).also {
                    opprettRevurdering(
                        sakId = it.toString(),
                        fraOgMed = stønadStart.toString(),
                        tilOgMed = stønadSlutt.toString(),
                    )
                },
            )

            val kontrollsamtaler = sakIds.map {
                kontrollsamtaleService.hentForSak(sakId = it).single()
            }

            tikkendeKlokke.spolTil(kontrollsamtaler.first().innkallingsdato)

            kontrollsamtaler.forEach {
                kontrollsamtaleService.kallInn(
                    sakId = it.sakId,
                    kontrollsamtale = it,
                )
            }

            val utløpsfristKontrollsamtale = kontrollsamtaler.first().frist
            tikkendeKlokke.spolTil(utløpsfristKontrollsamtale)

            // 1
            utløptFristForKontrollsamtaleService.håndterUtløpsdato(utløpsfristKontrollsamtale)

            appComponents.databaseRepos.jobContextRepo.hent<UtløptFristForKontrollsamtaleContext>(
                id = NameAndLocalDateId(
                    jobName = "KontrollsamtaleFristUtløptContext",
                    date = utløpsfristKontrollsamtale,
                ),
            )!!.also {
                it shouldBe UtløptFristForKontrollsamtaleContext(
                    id = NameAndLocalDateId(
                        jobName = "KontrollsamtaleFristUtløptContext",
                        date = utløpsfristKontrollsamtale,
                    ),
                    opprettet = it.opprettet(),
                    endret = it.endret(),
                    prosessert = setOf(
                        kontrollsamtaler[0].id,
                        kontrollsamtaler[1].id,
                        kontrollsamtaler[2].id,
                    ),
                    ikkeMøtt = setOf(
                        kontrollsamtaler[0].id,
                    ),
                    feilet = setOf(
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[3].id,
                            retries = 0,
                            feil = """class no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil${"\$"}KunneIkkeUtbetale""",
                            oppgaveId = null,
                        ),
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[4].id,
                            retries = 0,
                            feil = """class no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil${"\$"}KunneIkkeUtbetale""",
                            oppgaveId = null,
                        ),
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[5].id,
                            retries = 0,
                            feil = """class no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse${"\$"}SakHarÅpenBehandling""",
                            oppgaveId = null,
                        ),
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[6].id,
                            retries = 0,
                            feil = """class no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse${"\$"}SakHarÅpenBehandling""",
                            oppgaveId = null,
                        ),
                    ),
                )
            }

            // 2
            utløptFristForKontrollsamtaleService.håndterUtløpsdato(utløpsfristKontrollsamtale)

            appComponents.databaseRepos.jobContextRepo.hent<UtløptFristForKontrollsamtaleContext>(
                id = NameAndLocalDateId(
                    jobName = "KontrollsamtaleFristUtløptContext",
                    date = utløpsfristKontrollsamtale,
                ),
            )!!.also {
                it shouldBe UtløptFristForKontrollsamtaleContext(
                    id = NameAndLocalDateId(
                        jobName = "KontrollsamtaleFristUtløptContext",
                        date = utløpsfristKontrollsamtale,
                    ),
                    opprettet = it.opprettet(),
                    endret = it.endret(),
                    prosessert = setOf(
                        kontrollsamtaler[0].id,
                        kontrollsamtaler[1].id,
                        kontrollsamtaler[2].id,
                        kontrollsamtaler[4].id,
                    ),
                    ikkeMøtt = setOf(
                        kontrollsamtaler[0].id,
                        kontrollsamtaler[4].id,
                    ),
                    feilet = setOf(
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[3].id,
                            retries = 1,
                            feil = """class no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil${"\$"}KunneIkkeUtbetale""",
                            oppgaveId = null,
                        ),
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[5].id,
                            retries = 1,
                            feil = """class no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse${"\$"}SakHarÅpenBehandling""",
                            oppgaveId = null,
                        ),
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[6].id,
                            retries = 1,
                            feil = """class no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse${"\$"}SakHarÅpenBehandling""",
                            oppgaveId = null,
                        ),
                    ),
                )
                assertUendret(
                    saker = listOf(kontrollsamtaler[3].sakId),
                    periode = Periode.create(stønadStart, stønadSlutt),
                    sakService = appComponents.services.sak,
                    kontrollsamtaleService = appComponents.services.kontrollsamtale,
                )
            }

            // 3
            utløptFristForKontrollsamtaleService.håndterUtløpsdato(utløpsfristKontrollsamtale)
            // 4
            utløptFristForKontrollsamtaleService.håndterUtløpsdato(utløpsfristKontrollsamtale)

            appComponents.databaseRepos.jobContextRepo.hent<UtløptFristForKontrollsamtaleContext>(
                id = NameAndLocalDateId(
                    jobName = "KontrollsamtaleFristUtløptContext",
                    date = utløpsfristKontrollsamtale,
                ),
            )!!.also {
                it shouldBe UtløptFristForKontrollsamtaleContext(
                    id = NameAndLocalDateId(
                        jobName = "KontrollsamtaleFristUtløptContext",
                        date = utløpsfristKontrollsamtale,
                    ),
                    opprettet = it.opprettet(),
                    endret = it.endret(),
                    prosessert = setOf(
                        kontrollsamtaler[0].id,
                        kontrollsamtaler[1].id,
                        kontrollsamtaler[2].id,
                        kontrollsamtaler[4].id,
                        kontrollsamtaler[3].id,
                        kontrollsamtaler[5].id,
                    ),
                    ikkeMøtt = setOf(
                        kontrollsamtaler[0].id,
                        kontrollsamtaler[4].id,
                        kontrollsamtaler[3].id,
                    ),
                    feilet = setOf(
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[5].id,
                            retries = 2,
                            feil = """class no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse${"\$"}SakHarÅpenBehandling""",
                            oppgaveId = "stubbed",
                        ),
                        UtløptFristForKontrollsamtaleContext.Feilet(
                            id = kontrollsamtaler[6].id,
                            retries = 2,
                            feil = """class no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse${"\$"}SakHarÅpenBehandling""",
                            oppgaveId = null,
                        ),
                    ),
                )
                assertMøttTilSamtale(
                    saker = listOf(kontrollsamtaler[1].sakId, kontrollsamtaler[2].sakId),
                    periode = Periode.create(stønadStart, stønadSlutt),
                    sakService = appComponents.services.sak,
                    kontrollsamtaleService = appComponents.services.kontrollsamtale,
                )
                assertIkkeMøttTilSamtale(
                    saker = listOf(kontrollsamtaler[0].sakId, kontrollsamtaler[4].sakId, kontrollsamtaler[3].sakId),
                    periode = Periode.create(utløpsfristKontrollsamtale.førsteINesteMåned(), stønadSlutt),
                    sakService = appComponents.services.sak,
                    kontrollsamtaleService = appComponents.services.kontrollsamtale,
                )
            }
            statistikkCaptor.allValues.filter { it.contains(""""behandlingStatus":"REGISTRERT"""") && it.contains(""""resultatBegrunnelse":"MANGLENDE_KONTROLLERKLÆRING"""") } shouldHaveSize 3
            statistikkCaptor.allValues.filter { it.contains(""""behandlingStatus":"IVERKSATT"""") && it.contains(""""resultatBegrunnelse":"MANGLENDE_KONTROLLERKLÆRING"""") } shouldHaveSize 3
            statistikkCaptor.allValues.filter { it.contains(""""vedtaksresultat":"STANSET"""") } shouldHaveSize 3
        }
    }

    private fun ApplicationTestBuilder.innvilgSøknad(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
    ): UUID {
        return opprettInnvilgetSøknadsbehandling(
            fnr = Fnr.generer().toString(),
            fraOgMed = fraOgMed.toString(),
            tilOgMed = tilOgMed.toString(),
        ).let {
            hentSak(BehandlingJson.hentSakId(it)).let { sakJson ->
                UUID.fromString(hentSakId(sakJson))
            }
        }
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

    private fun assertIkkeMøttTilSamtale(saker: List<UUID>, periode: Periode, sakService: SakService, kontrollsamtaleService: KontrollsamtaleService) {
        saker.forEach { sakId ->
            sakService.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger.single().shouldBeType<StansAvYtelseRevurdering.IverksattStansAvYtelse>()
                sak.vedtakListe.single { it is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse }
                sak.vedtakstidslinje().also { vedtakstidslinje ->
                    periode.måneder().map {
                        vedtakstidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak to sak.utbetalingstidslinje(it).gjeldendeForDato(it.fraOgMed)
                    }.forEach { (vedtak, utbetaling) ->
                        (vedtak is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse && utbetaling is UtbetalingslinjePåTidslinje.Stans) shouldBe true
                    }
                }
                kontrollsamtaleService.hentForSak(sak.id).single { it.status == Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST }.let {
                    it.journalpostIdKontrollnotat shouldBe beNull()
                }
            }
        }
    }

    private fun assertMøttTilSamtale(saker: List<UUID>, periode: Periode, sakService: SakService, kontrollsamtaleService: KontrollsamtaleService) {
        saker.forEach { sakId ->
            sakService.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger shouldBe emptyList()
                sak.vedtakListe.single()
                sak.vedtakstidslinje().also { vedtakstidslinje ->
                    periode.måneder().map {
                        vedtakstidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak to sak.utbetalingstidslinje(it).gjeldendeForDato(it.fraOgMed)
                    }.forEach { (vedtak, utbetaling) ->
                        (vedtak is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling && utbetaling is UtbetalingslinjePåTidslinje.Ny) shouldBe true
                    }
                }
                kontrollsamtaleService.hentForSak(sak.id).single { it.status == Kontrollsamtalestatus.GJENNOMFØRT }.let {
                    it.journalpostIdKontrollnotat shouldNot beNull()
                }
            }
        }
    }
    private fun assertUendret(saker: List<UUID>, periode: Periode, sakService: SakService, kontrollsamtaleService: KontrollsamtaleService) {
        saker.forEach { sakId ->
            sakService.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger shouldBe emptyList()
                sak.vedtakListe.single()
                sak.vedtakstidslinje().let { tidslinje ->
                    periode.måneder().map {
                        tidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak to sak.utbetalingstidslinje(it).gjeldendeForDato(it.fraOgMed)
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
