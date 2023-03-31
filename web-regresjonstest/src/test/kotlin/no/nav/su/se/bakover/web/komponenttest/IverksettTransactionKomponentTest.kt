package no.nav.su.se.bakover.web.komponenttest

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.revurdering.gjenopptak.iverksettGjenopptak
import no.nav.su.se.bakover.web.revurdering.gjenopptak.opprettGjenopptak
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.stans.iverksettStans
import no.nav.su.se.bakover.web.stans.opprettStans
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.avslåttFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.iverksett.iverksett
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import no.nav.su.se.bakover.web.revurdering.iverksett.iverksett as iverksettRevurdering

internal class IverksettTransactionKomponentTest {
    @Test
    fun `ruller tilbake søknadsbehandling hvis feil ved iverksettelse`() {
        val klokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val start = LocalDate.now(klokke).startOfMonth()
        val slutt = start.plusMonths(11).endOfMonth()

        withKomptestApplication(
            clock = klokke,
            clientsBuilder = { databaseRepos, clock ->
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).let {
                    it.copy(
                        utbetalingPublisher = object : UtbetalingPublisher by it.utbetalingPublisher {
                            override fun publishRequest(utbetalingsrequest: Utbetalingsrequest): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
                                return UtbetalingPublisher.KunneIkkeSendeUtbetaling(Utbetalingsrequest("det gikk dårlig")).left()
                            }
                        },
                    )
                }
            },
        ) { appComponents ->
            val sakId = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = start.toString(),
                tilOgMed = slutt.toString(),
                iverksett = { _, _ -> SKIP_STEP },
                client = this.client,
            ).let { søknadsbehandling ->
                val (sakId, behandlingId) = hentSak(BehandlingJson.hentSakId(søknadsbehandling), client = this.client).let { sakJson ->
                    hentSakId(sakJson) to BehandlingJson.hentBehandlingId(søknadsbehandling)
                }

                iverksett(
                    sakId = sakId,
                    behandlingId = behandlingId,
                    assertResponse = false,
                    client = this.client,
                ).let {
                    JSONObject(it).getString("code") shouldBe "ukjent_feil"
                }
                sakId
            }

            appComponents.services.sak.hentSak(UUID.fromString(sakId)).getOrFail().also {
                it.utbetalinger shouldBe emptyList()
                it.søknadsbehandlinger.single().shouldBeType<SøknadsbehandlingTilAttestering.Innvilget>()
                it.vedtakListe shouldBe emptyList()
                appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(it.id) shouldBe KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale.left()
            }
        }
    }

    @Test
    fun `ruller tilbake innvilget revurdering hvis feil ved iverksettelse`() {
        val klokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val start = LocalDate.now(klokke).startOfMonth()
        val slutt = start.plusMonths(11).endOfMonth()

        withKomptestApplication(
            clock = klokke,
            clientsBuilder = { databaseRepos, clock ->
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).let {
                    it.copy(
                        utbetalingPublisher = object : UtbetalingPublisher by it.utbetalingPublisher {
                            var count = 0
                            override fun publishRequest(utbetalingsrequest: Utbetalingsrequest): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
                                count++
                                return if (count == 2) {
                                    UtbetalingPublisher.KunneIkkeSendeUtbetaling(Utbetalingsrequest("det gikk dårlig")).left()
                                } else {
                                    utbetalingsrequest.right()
                                }
                            }
                        },
                    )
                }
            },
        ) { appComponents ->
            opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = start.toString(),
                tilOgMed = slutt.toString(),
                client = this.client,
            ).let { søknadsbehandling ->
                val sakId = hentSakId(hentSak(BehandlingJson.hentSakId(søknadsbehandling), client = this.client))

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = start.plusMonths(4).startOfMonth().toString(),
                    tilogmed = start.plusMonths(6).endOfMonth().toString(),
                    iverksett = { _, _ -> SKIP_STEP },
                    client = this.client,
                ).let {
                    iverksettRevurdering(
                        sakId = sakId,
                        behandlingId = RevurderingJson.hentRevurderingId(it),
                        assertResponse = false,
                        client = this.client,
                    ).let {
                        JSONObject(it).getString("code") shouldBe "kunne_ikke_utbetale"
                    }
                }

                appComponents.services.sak.hentSak(UUID.fromString(sakId)).getOrFail().also { sak ->
                    sak.søknadsbehandlinger.single().shouldBeType<IverksattSøknadsbehandling.Innvilget>()
                    sak.vedtakListe.single().shouldBeType<VedtakInnvilgetSøknadsbehandling>().also {
                        sak.utbetalinger.single().id shouldBe it.utbetalingId
                    }
                    sak.revurderinger.single().shouldBeType<RevurderingTilAttestering.Innvilget>()
                    appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sak.id).getOrFail().shouldBeType<Kontrollsamtale>()
                }
            }
        }
    }

    @Test
    fun `ruller tilbake opphørt revurdering hvis feil ved iverksettelse`() {
        val klokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val start = LocalDate.now(klokke).startOfMonth()
        val slutt = start.plusMonths(11).endOfMonth()

        withKomptestApplication(
            clock = klokke,
            clientsBuilder = { databaseRepos, clock ->
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).let {
                    it.copy(
                        utbetalingPublisher = object : UtbetalingPublisher by it.utbetalingPublisher {
                            var count = 0
                            override fun publishRequest(utbetalingsrequest: Utbetalingsrequest): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
                                count++
                                return if (count == 2) {
                                    UtbetalingPublisher.KunneIkkeSendeUtbetaling(Utbetalingsrequest("det gikk dårlig")).left()
                                } else {
                                    utbetalingsrequest.right()
                                }
                            }
                        },
                    )
                }
            },
        ) { appComponents ->
            opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = start.toString(),
                tilOgMed = slutt.toString(),
                client = this.client,
            ).let { søknadsbehandling ->
                val sakId = hentSakId(hentSak(BehandlingJson.hentSakId(søknadsbehandling), client = this.client))

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = start.plusMonths(4).startOfMonth().toString(),
                    tilogmed = start.plusMonths(6).endOfMonth().toString(),
                    leggTilFlyktningVilkår = { sakid, behandlingId, fraOgMed, tilOgMed, _, url ->
                        leggTilFlyktningVilkår(
                            sakId = sakid,
                            behandlingId = behandlingId,
                            fraOgMed = fraOgMed,
                            tilOgMed = tilOgMed,
                            body = { avslåttFlyktningVilkårJson(fraOgMed, tilOgMed) },
                            url = url,
                            client = this.client,
                        )
                    },
                    iverksett = { _, _ -> SKIP_STEP },
                    client = this.client,
                ).let {
                    iverksettRevurdering(
                        sakId = sakId,
                        behandlingId = RevurderingJson.hentRevurderingId(it),
                        assertResponse = false,
                        client = this.client,
                    ).let {
                        JSONObject(it).getString("code") shouldBe "kunne_ikke_utbetale"
                    }
                }

                appComponents.services.sak.hentSak(UUID.fromString(sakId)).getOrFail().also { sak ->
                    sak.søknadsbehandlinger.single().shouldBeType<IverksattSøknadsbehandling.Innvilget>()
                    sak.vedtakListe.single().shouldBeType<VedtakInnvilgetSøknadsbehandling>().also {
                        sak.utbetalinger.single().id shouldBe it.utbetalingId
                    }
                    sak.revurderinger.single().shouldBeType<RevurderingTilAttestering.Opphørt>()
                    appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sak.id).getOrFail().shouldBeType<Kontrollsamtale>()
                }
            }
        }
    }

    @Test
    fun `ruller tilbake stans hvis feil ved iverksettelse`() {
        val klokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val start = LocalDate.now(klokke).startOfMonth()
        val slutt = start.plusMonths(11).endOfMonth()

        withKomptestApplication(
            clock = klokke,
            clientsBuilder = { databaseRepos, clock ->
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).let {
                    it.copy(
                        utbetalingPublisher = object : UtbetalingPublisher by it.utbetalingPublisher {
                            var count = 0
                            override fun publishRequest(utbetalingsrequest: Utbetalingsrequest): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
                                count++
                                return if (count == 2) {
                                    UtbetalingPublisher.KunneIkkeSendeUtbetaling(Utbetalingsrequest("det gikk dårlig")).left()
                                } else {
                                    utbetalingsrequest.right()
                                }
                            }
                        },
                    )
                }
            },
        ) { appComponents ->
            opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = start.toString(),
                tilOgMed = slutt.toString(),
                client = this.client,
            ).let { søknadsbehandling ->
                val sakId = hentSakId(hentSak(BehandlingJson.hentSakId(søknadsbehandling), client = this.client))

                val stansId = opprettStans(
                    sakId,
                    start.toString(),
                    client = this.client,
                ).let { RevurderingJson.hentRevurderingId(it) }
                iverksettStans(
                    sakId = sakId,
                    behandlingId = stansId,
                    assertResponse = false,
                    client = this.client,
                )

                appComponents.services.sak.hentSak(UUID.fromString(sakId)).getOrFail().also { sak ->
                    sak.søknadsbehandlinger.single().shouldBeType<IverksattSøknadsbehandling.Innvilget>()
                    sak.vedtakListe.also { vedtakListe ->
                        vedtakListe.single { it is VedtakInnvilgetSøknadsbehandling }
                    }
                    sak.utbetalinger.also { utbetalinger ->
                        utbetalinger shouldHaveSize 1
                        utbetalinger.map { it.id }.shouldContainAll(sak.vedtakListe.map { (it as VedtakEndringIYtelse).utbetalingId })
                    }
                    sak.revurderinger.also { revurderinger ->
                        revurderinger shouldHaveSize 1
                        revurderinger.single { it is StansAvYtelseRevurdering.SimulertStansAvYtelse }
                    }
                    appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sak.id).getOrFail().shouldBeType<Kontrollsamtale>()
                }
            }
        }
    }

    @Test
    fun `ruller tilbake gjenopptak hvis feil ved iverksettelse`() {
        val klokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val start = LocalDate.now(klokke).startOfMonth()
        val slutt = start.plusMonths(11).endOfMonth()

        withKomptestApplication(
            clock = klokke,
            clientsBuilder = { databaseRepos, clock ->
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).let {
                    it.copy(
                        utbetalingPublisher = object : UtbetalingPublisher by it.utbetalingPublisher {
                            var count = 0
                            override fun publishRequest(utbetalingsrequest: Utbetalingsrequest): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
                                count++
                                return if (count == 3) {
                                    UtbetalingPublisher.KunneIkkeSendeUtbetaling(Utbetalingsrequest("det gikk dårlig")).left()
                                } else {
                                    utbetalingsrequest.right()
                                }
                            }
                        },
                    )
                }
            },
        ) { appComponents ->
            opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = start.toString(),
                tilOgMed = slutt.toString(),
                client = this.client,
            ).let { søknadsbehandling ->
                val sakId = hentSakId(hentSak(BehandlingJson.hentSakId(søknadsbehandling), client = this.client))

                val stansId = opprettStans(sakId, start.toString(), client = this.client).let { RevurderingJson.hentRevurderingId(it) }
                iverksettStans(sakId, stansId, client = this.client)
                val gjenopptakId = opprettGjenopptak(sakId, client = this.client).let { RevurderingJson.hentRevurderingId(it) }
                iverksettGjenopptak(
                    sakId = sakId,
                    behandlingId = gjenopptakId,
                    assertResponse = false,
                    client = this.client,
                ).let {
                    JSONObject(it).getString("code") shouldBe "kunne_ikke_utbetale"
                }

                appComponents.services.sak.hentSak(UUID.fromString(sakId)).getOrFail().also { sak ->
                    sak.søknadsbehandlinger.single().shouldBeType<IverksattSøknadsbehandling.Innvilget>()
                    sak.vedtakListe.also { vedtakListe ->
                        vedtakListe shouldHaveSize 2
                        vedtakListe.single { it is VedtakInnvilgetSøknadsbehandling }
                        vedtakListe.single { it is VedtakStansAvYtelse }
                    }
                    sak.utbetalinger.also { utbetalinger ->
                        utbetalinger shouldHaveSize 2
                        utbetalinger.map { it.id }.shouldContainAll(sak.vedtakListe.map { (it as VedtakEndringIYtelse).utbetalingId })
                    }
                    sak.revurderinger.also { revurderinger ->
                        revurderinger shouldHaveSize 2
                        revurderinger.single { it is StansAvYtelseRevurdering.IverksattStansAvYtelse }
                        revurderinger.single { it is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse }
                    }
                    appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sak.id).getOrFail().shouldBeType<Kontrollsamtale>()
                }
            }
        }
    }
}
