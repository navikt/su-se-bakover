package no.nav.su.se.bakover.database.sak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.søknadNy
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.SakIdOgNummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test

internal class SakPostgresRepoTest {

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val nySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            val opprettet: Sak = repo.hentSak(nySak.fnr)!!
            val hentetId = repo.hentSak(opprettet.id)!!
            val hentetFnr = repo.hentSak(opprettet.fnr)!!

            opprettet shouldBe hentetId
            hentetId shouldBe hentetFnr

            opprettet.fnr shouldBe nySak.fnr
            opprettet.id shouldBe nySak.id
            opprettet.opprettet shouldBe nySak.opprettet
            opprettet.søknadNy() shouldBe nySak.søknad
        }
    }

    @Test
    fun `hent sakId fra fnr liste med 1 element`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val nySak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            repo.hentSakIdOgNummerForIdenter(nonEmptyListOf(nySak.fnr.toString())) shouldBe SakIdOgNummer(nySak.id, nySak.saksnummer)
        }
    }

    @Test
    fun `hent sakId fra fnr liste med 2 elementer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val nySak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            repo.hentSakIdOgNummerForIdenter(nonEmptyListOf("1234567890123", nySak.fnr.toString())) shouldBe SakIdOgNummer(nySak.id, nySak.saksnummer)
            repo.hentSakIdOgNummerForIdenter(nonEmptyListOf(nySak.fnr.toString(), "1234567890123")) shouldBe SakIdOgNummer(nySak.id, nySak.saksnummer)
        }
    }

    @Test
    fun `hent sakId fra fnr liste med 3 elementer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val nySak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            repo.hentSakIdOgNummerForIdenter(
                nonEmptyListOf(
                    "1234567890123",
                    nySak.fnr.toString(),
                    "1234567890123",
                ),
            ) shouldBe SakIdOgNummer(nySak.id, nySak.saksnummer)
        }
    }

    @Test
    fun `henter ikke ut lukkede eller avsluttede behandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sak.id)
            testDataHelper.persisterRevurderingAvsluttet()
            testDataHelper.persisterKlageAvsluttet()

            val åpneBehandlinger = repo.hentÅpneBehandlinger()

            åpneBehandlinger.size shouldBe 1

            åpneBehandlinger shouldBe listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(2021),
                    behandlingsId = sak.søknad.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = null,
                    status = Behandlingsoversikt.Behandlingsstatus.NY_SØKNAD,
                ),
            )
        }
    }

    @Test
    fun `Henter alle restanser for alle saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sakId = sak.id)
            testDataHelper.persisterSøknadsbehandlingAvsluttet(sakId = sak.id)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val underkjent = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagMedBeregning().second
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning().second
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

            val opprettetRevurdering =
                testDataHelper.persisterRevurderingOpprettet(
                    testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().second,
                    stønadsperiode2021.periode,
                )
            val tilAttesteringRevurdering = testDataHelper.persisterRevurderingTilAttesteringInnvilget()
            val underkjentRevurdering = testDataHelper.persisterRevurderingUnderkjentInnvilget()
            testDataHelper.persisterRevurderingIverksattInnvilget()

            val opprettetKlage = testDataHelper.persisterKlageOpprettet()
            val vurdertKlage = testDataHelper.persisterKlageVurdertPåbegynt()
            val klageTilAttestering = testDataHelper.persisterKlageTilAttesteringAvvist()
            testDataHelper.persisterKlageIverksattAvvist()

            val alleRestanser = repo.hentÅpneBehandlinger()

            alleRestanser.size shouldBe 10

            alleRestanser shouldContainExactlyInAnyOrder listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2021),
                    behandlingsId = sak.søknad.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.NY_SØKNAD,
                    behandlingStartet = null,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2022),
                    behandlingsId = søknadsbehandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = søknadsbehandling.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2023),
                    behandlingsId = underkjent.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDERKJENT,
                    behandlingStartet = underkjent.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2024),
                    behandlingsId = tilAttestering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttestering.opprettet,
                ),
                // Vi hopper over 1 saksnummer siden den blir lagret som en del når vi lager en revurdering gjennom
                // hjelpe funksjoner
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2026),
                    behandlingsId = opprettetRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = opprettetRevurdering.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2027),
                    behandlingsId = tilAttesteringRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    status = Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttesteringRevurdering.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2028),
                    behandlingsId = underkjentRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDERKJENT,
                    behandlingStartet = underkjentRevurdering.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2030),
                    behandlingsId = opprettetKlage.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.KLAGE,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = opprettetKlage.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2031),
                    behandlingsId = vurdertKlage.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.KLAGE,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = vurdertKlage.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2032),
                    behandlingsId = klageTilAttestering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.KLAGE,
                    status = Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = klageTilAttestering.opprettet,
                ),
            )
        }
    }
}
