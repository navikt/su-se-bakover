package no.nav.su.se.bakover.database.sak

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.test.enUkeEtterFixedTidspunkt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test

internal class FerdigeBehandlingerRepoTest {

    @Test
    fun `henter alle ferdige søkandsbehandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            val iverksattSøknadsbehandlingInnvilget =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilget().second
            val iverksattSøknadsbehadnlingAvslagMedBeregning =
                testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            val iverksattSøknadsbehadnlingAvslagUtenBeregning =
                testDataHelper.persisterSøknadsbehandlingIverksattAvslagUtenBeregning().second

            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()
            testDataHelper.persisterSøknadsbehandlingBeregnetAvslag()

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            ferdigeBehandlinger.size shouldBe 3

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = iverksattSøknadsbehandlingInnvilget.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehandlingInnvilget.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = iverksattSøknadsbehadnlingAvslagMedBeregning.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehadnlingAvslagMedBeregning.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.AVSLAG,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattSøknadsbehadnlingAvslagUtenBeregning.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehadnlingAvslagUtenBeregning.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.AVSLAG,
                ),
            )
        }
    }

    @Test
    fun `henter ferdige revurderinger for alle saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            val iverksattRevurderingInnvilget = testDataHelper.persisterRevurderingIverksattInnvilget()
            val iverksattRevurderingOpphørt = testDataHelper.persisterRevurderingIverksattOpphørt()
            val iverksattStansAvYtelse = testDataHelper.persisterStansAvYtelseIverksatt()
            val iverksattGjenopptak = testDataHelper.persisterGjenopptakAvYtelseIverksatt()
            val beregnetRevurdering = testDataHelper.persisterRevurderingBeregnetInnvilget()

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            // hver revurdering får en tilhørende iverksatt søknadsbehandling
            ferdigeBehandlinger.size shouldBe 9

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = iverksattRevurderingInnvilget.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = enUkeEtterFixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = iverksattRevurderingInnvilget.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = fixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = iverksattRevurderingOpphørt.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = enUkeEtterFixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = iverksattRevurderingOpphørt.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = fixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.OPPHØR,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattStansAvYtelse.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = enUkeEtterFixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattStansAvYtelse.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = enUkeEtterFixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.STANS,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = iverksattGjenopptak.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = enUkeEtterFixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = iverksattGjenopptak.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = enUkeEtterFixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.GJENOPPTAK,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2025),
                    behandlingsId = beregnetRevurdering.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = enUkeEtterFixedTidspunkt,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
            )
        }
    }

    @Test
    fun `henter ferdige klager for alle saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            val vedtakSak1 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second
            val oversendtKlage = testDataHelper.persisterKlageOversendt(vedtakSak1)

            val vedtakSak2 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second
            val iverksattAvvistKlage = testDataHelper.persisterKlageIverksattAvvist(vedtakSak2)

            val vedtakSak3 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second
            testDataHelper.persisterKlageOpprettet(vedtakSak3)

            val vedtakSak4 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second
            testDataHelper.persisterKlageVurdertBekreftet(vedtakSak4)

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            // hver klage får en tilhørende iverksatt søknadsbehandling
            ferdigeBehandlinger.size shouldBe 6

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = vedtakSak1.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak1.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = oversendtKlage.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.KLAGE,
                    behandlingStartet = oversendtKlage.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.OVERSENDT,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = vedtakSak2.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak2.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = iverksattAvvistKlage.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.KLAGE,
                    behandlingStartet = iverksattAvvistKlage.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.AVSLAG,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = vedtakSak3.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak3.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = vedtakSak4.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak4.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
            )
        }
    }

    @Test
    fun `henter ikke avsluttede behandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            testDataHelper.persisterLukketJournalførtSøknadMedOppgave() // 2021
            testDataHelper.persisterSøknadsbehandlingAvsluttet() // 2022
            val revurdering = testDataHelper.persisterRevurderingAvsluttet() // 2023
            val klage =
                testDataHelper.persisterKlageAvsluttet().hentUnderliggendeKlage() as VurdertKlage.Bekreftet // 2024

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            ferdigeBehandlinger.map { it.behandlingsId }
                .sorted() shouldBe listOf(
                revurdering.tilRevurdering.behandling.id,
                testDataHelper.sakRepo.hentSak(klage.sakId)!!.søknadsbehandlinger.first().id,
            ).sorted()
        }
    }
}
