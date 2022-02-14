package no.nav.su.se.bakover.database.sak

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test

internal class FerdigeBehandlingerRepoTest {

    @Test
    fun `henter alle ferdige søkandsbehandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val sak = testDataHelper.nySakMedNySøknad()

            val lukketSøknadsbehandling =
                testDataHelper.nyLukketSøknadsbehandlingOgSøknadForEksisterendeSak(sak.toSak(saksnummer))
            val iverksattSøknadsbehandlingInnvilget = testDataHelper.nyIverksattInnvilget().first
            val iverksattSøknadsbehadnlingAvslagMedBeregning = testDataHelper.nyIverksattAvslagMedBeregning()
            val iverksattSøknadsbehadnlingAvslagUtenBeregning = testDataHelper.nyIverksattAvslagUtenBeregning()

            testDataHelper.nyInnvilgetVilkårsvurdering()
            testDataHelper.nyAvslåttBeregning()

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            ferdigeBehandlinger.size shouldBe 4

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = lukketSøknadsbehandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = lukketSøknadsbehandling.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.AVSLUTTET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = iverksattSøknadsbehandlingInnvilget.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehandlingInnvilget.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattSøknadsbehadnlingAvslagMedBeregning.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehadnlingAvslagMedBeregning.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.AVSLAG,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = iverksattSøknadsbehadnlingAvslagUtenBeregning.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehadnlingAvslagUtenBeregning.opprettet,
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

            val iverksattRevurderingInnvilget = testDataHelper.iverksattRevurderingInnvilget()
            val avsluttetRevurdering = testDataHelper.avsluttetRevurdering()
            val iverksattRevurderingOpphørt = testDataHelper.iverksattRevurderingOpphørt()

            val beregnetRevurdering = testDataHelper.beregnetInnvilgetRevurdering()

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            // hver revurdering får en tilhørende iverksatt søknadsbehandling
            ferdigeBehandlinger.size shouldBe 7

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = iverksattRevurderingInnvilget.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattRevurderingInnvilget.tilRevurdering.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = iverksattRevurderingInnvilget.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattRevurderingInnvilget.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = avsluttetRevurdering.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = avsluttetRevurdering.tilRevurdering.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = avsluttetRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = avsluttetRevurdering.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.AVSLUTTET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattRevurderingOpphørt.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattRevurderingOpphørt.tilRevurdering.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattRevurderingOpphørt.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattRevurderingOpphørt.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.OPPHØR,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = beregnetRevurdering.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = beregnetRevurdering.tilRevurdering.behandling.opprettet,
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

            val vedtakSak1 = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val oversendtKlage = testDataHelper.oversendtKlage(vedtakSak1)

            val vedtakSak2 = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val iverksattAvvistKlage = testDataHelper.iverksattAvvistKlage(vedtakSak2)

            val vedtakSak3 = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val avsluttetKlage = testDataHelper.avsluttetKlage(vedtakSak3)

            val vedtakSak4 = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            testDataHelper.nyKlage(vedtakSak4)

            val vedtakSak5 = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            testDataHelper.bekreftetVurdertKlage(vedtakSak5)

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            // hver klage får en tilhørende iverksatt søknadsbehandling
            ferdigeBehandlinger.size shouldBe 8

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = vedtakSak1.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak1.behandling.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = oversendtKlage.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.KLAGE,
                    behandlingStartet = oversendtKlage.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = vedtakSak2.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak2.behandling.opprettet,
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
                    behandlingStartet = vedtakSak3.behandling.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = avsluttetKlage.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.KLAGE,
                    behandlingStartet = avsluttetKlage.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.AVSLUTTET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = vedtakSak4.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak4.behandling.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2025),
                    behandlingsId = vedtakSak5.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak5.behandling.opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
            )
        }
    }
}
