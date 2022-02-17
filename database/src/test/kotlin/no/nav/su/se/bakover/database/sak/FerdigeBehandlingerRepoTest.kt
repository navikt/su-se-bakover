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

            val iverksattSøknadsbehandlingInnvilget = testDataHelper.nyIverksattInnvilget().first
            val iverksattSøknadsbehadnlingAvslagMedBeregning = testDataHelper.nyIverksattAvslagMedBeregning()
            val iverksattSøknadsbehadnlingAvslagUtenBeregning = testDataHelper.nyIverksattAvslagUtenBeregning()

            testDataHelper.nyInnvilgetVilkårsvurdering()
            testDataHelper.nyAvslåttBeregning()

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

            val iverksattRevurderingInnvilget = testDataHelper.iverksattRevurderingInnvilget()
            val iverksattRevurderingOpphørt = testDataHelper.iverksattRevurderingOpphørt()
            val iverksattStansAvYtelse = testDataHelper.iverksattStans()
            val iverksattGjenopptak = testDataHelper.iverksettGjenopptak()
            val beregnetRevurdering = testDataHelper.beregnetInnvilgetRevurdering()

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            // hver revurdering får en tilhørende iverksatt søknadsbehandling
            ferdigeBehandlinger.size shouldBe 9

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = iverksattRevurderingInnvilget.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattRevurderingInnvilget.tilRevurdering.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = iverksattRevurderingInnvilget.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattRevurderingInnvilget.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = iverksattRevurderingOpphørt.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattRevurderingOpphørt.tilRevurdering.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = iverksattRevurderingOpphørt.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattRevurderingOpphørt.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.OPPHØR,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattStansAvYtelse.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattStansAvYtelse.tilRevurdering.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = iverksattStansAvYtelse.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattStansAvYtelse.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.STANS,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = iverksattGjenopptak.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattGjenopptak.tilRevurdering.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2024),
                    behandlingsId = iverksattGjenopptak.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattGjenopptak.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.GJENOPPTAK,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2025),
                    behandlingsId = beregnetRevurdering.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = beregnetRevurdering.tilRevurdering.behandling.attesteringer.hentSisteAttestering().opprettet,
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
            testDataHelper.nyKlage(vedtakSak3)

            val vedtakSak4 = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            testDataHelper.bekreftetVurdertKlage(vedtakSak4)

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
            val sak = testDataHelper.nySakMedNySøknad()

            testDataHelper.nyLukketSøknadsbehandlingOgSøknadForEksisterendeSak(sak.toSak(saksnummer))
            val avsluttetRevurdering = testDataHelper.avsluttetRevurdering()

            val vedtakForKlage = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            testDataHelper.avsluttetKlage(vedtakForKlage)

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            ferdigeBehandlinger.size shouldBe 2

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2022),
                    behandlingsId = avsluttetRevurdering.tilRevurdering.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = avsluttetRevurdering.tilRevurdering.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2023),
                    behandlingsId = vedtakForKlage.behandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakForKlage.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingsoversikt.Behandlingsstatus.INNVILGET,
                ),
            )
        }
    }
}
