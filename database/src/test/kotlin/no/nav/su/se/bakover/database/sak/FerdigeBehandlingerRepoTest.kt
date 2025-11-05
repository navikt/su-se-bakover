package no.nav.su.se.bakover.database.sak

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.shouldBeType
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
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2021),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehandlingInnvilget.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2022),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehadnlingAvslagMedBeregning.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.AVSLAG,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2023),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = iverksattSøknadsbehadnlingAvslagUtenBeregning.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.AVSLAG,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
            )
        }
    }

    @Test
    fun `henter ferdige behandlinger for alle saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            val iverksattRevurderingInnvilget = testDataHelper.persisterRevurderingIverksattInnvilget().second
            val iverksattRevurderingOpphørt = testDataHelper.persisterRevurderingIverksattOpphørt().second
            val (_, iverksattStansAvYtelse) = testDataHelper.persisterIverksattStansOgVedtak()
            val iverksattGjenopptak = testDataHelper.persisterGjenopptakAvYtelseIverksatt()
            val (_, beregnetRevurdering) = testDataHelper.persisterBeregnetRevurdering()

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            // hver revurdering får en tilhørende iverksatt søknadsbehandling
            ferdigeBehandlinger.size shouldBe 9

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                testDataHelper.vedtakRepo.hentVedtakForId(iverksattRevurderingInnvilget.tilRevurdering)!!
                    .shouldBeType<VedtakInnvilgetSøknadsbehandling>().behandling.let {
                        Behandlingssammendrag(
                            saksnummer = Saksnummer(2021),
                            behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                            behandlingStartet = it.attesteringer.hentSisteAttestering().opprettet,
                            status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                            periode = år(2021),
                            sakType = Sakstype.UFØRE,
                        )
                    },
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2021),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattRevurderingInnvilget.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                testDataHelper.vedtakRepo.hentVedtakForId(iverksattRevurderingOpphørt.tilRevurdering)!!
                    .shouldBeType<VedtakInnvilgetSøknadsbehandling>().behandling.let {
                        Behandlingssammendrag(
                            saksnummer = Saksnummer(2022),
                            behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                            behandlingStartet = it.attesteringer.hentSisteAttestering().opprettet,
                            status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                            periode = år(2021),
                            sakType = Sakstype.UFØRE,
                        )
                    },

                Behandlingssammendrag(
                    saksnummer = Saksnummer(2022),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattRevurderingOpphørt.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.OPPHØR,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                testDataHelper.vedtakRepo.hentVedtakForId(iverksattStansAvYtelse.behandling.tilRevurdering)!!
                    .shouldBeType<VedtakInnvilgetSøknadsbehandling>().behandling.let {
                        Behandlingssammendrag(
                            saksnummer = Saksnummer(2023),
                            behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                            behandlingStartet = it.attesteringer.hentSisteAttestering().opprettet,
                            status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                            periode = år(2021),
                            sakType = Sakstype.UFØRE,
                        )
                    },

                Behandlingssammendrag(
                    saksnummer = Saksnummer(2023),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattStansAvYtelse.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.STANS,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                testDataHelper.vedtakRepo.hentVedtakForId(iverksattGjenopptak.tilRevurdering)!!
                    .shouldBeType<VedtakInnvilgetSøknadsbehandling>().behandling.let {
                        Behandlingssammendrag(
                            saksnummer = Saksnummer(2024),
                            behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                            behandlingStartet = it.attesteringer.hentSisteAttestering().opprettet,
                            status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                            periode = år(2021),
                            sakType = Sakstype.UFØRE,
                        )
                    },
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2024),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.REVURDERING,
                    behandlingStartet = iverksattGjenopptak.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.GJENOPPTAK,
                    periode = februar(2021)..desember(2021),
                    sakType = Sakstype.UFØRE,
                ),
                testDataHelper.vedtakRepo.hentVedtakForId(beregnetRevurdering.tilRevurdering)!!
                    .shouldBeType<VedtakInnvilgetSøknadsbehandling>().behandling.let {
                        Behandlingssammendrag(
                            saksnummer = Saksnummer(2025),
                            behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                            behandlingStartet = it.attesteringer.hentSisteAttestering().opprettet,
                            status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                            periode = år(2021),
                            sakType = Sakstype.UFØRE,
                        )
                    },
            )
        }
    }

    @Test
    fun `henter ferdige klager for alle saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            val vedtakSak1 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val oversendtKlage = testDataHelper.persisterKlageOversendt(vedtakSak1)

            val vedtakSak2 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val iverksattAvvistKlage = testDataHelper.persisterKlageIverksattAvvist(vedtakSak2)

            val vedtakSak3 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            testDataHelper.persisterKlageOpprettet(vedtakSak3)

            val vedtakSak4 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            testDataHelper.persisterKlageVurdertBekreftet(vedtakSak4)

            val ferdigeBehandlinger = repo.hentFerdigeBehandlinger()

            // hver klage får en tilhørende iverksatt søknadsbehandling
            ferdigeBehandlinger.size shouldBe 6

            ferdigeBehandlinger shouldContainExactlyInAnyOrder listOf(
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2021),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak1.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2021),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.KLAGE,
                    behandlingStartet = oversendtKlage.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.OVERSENDT,
                    periode = null,
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2022),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak2.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2022),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.KLAGE,
                    behandlingStartet = iverksattAvvistKlage.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.AVSLAG,
                    periode = null,
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2023),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak3.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2024),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = vedtakSak4.behandling.attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
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
            val (revurderingSak, _) = testDataHelper.persisterRevurderingAvsluttet() // 2023

            val klage = testDataHelper.persisterKlageAvsluttet()

            val klageSak = testDataHelper.sakRepo.hentSak(klage.sakId)!!
            repo.hentFerdigeBehandlinger().sortedBy { it.saksnummer.nummer } shouldBe listOf(
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2023),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    // Merk at det ikke er når behandlingen ble startet, men når den var iverksatt?
                    behandlingStartet = revurderingSak.søknadsbehandlinger.single().attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2024),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = klageSak.søknadsbehandlinger.single().attesteringer.hentSisteAttestering().opprettet,
                    status = Behandlingssammendrag.Behandlingsstatus.INNVILGET,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),

            )
        }
    }
}
