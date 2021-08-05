package no.nav.su.se.bakover.database.sak

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.søknadNy
import no.nav.su.se.bakover.database.stønadsperiode
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.SakRestans
import org.junit.jupiter.api.Test

internal class SakPostgresRepoTest {

    private val testDataHelper = TestDataHelper()
    private val repo = testDataHelper.sakRepo

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb {
            val nySak = testDataHelper.nySakMedNySøknad()
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
    fun `Henter alle restanser for alle saker`() {
        withMigratedDb {
            val sak = testDataHelper.nySakMedNySøknad()
            testDataHelper.nyLukketSøknadForEksisterendeSak(sak.id)
            val søknadsbehandling = testDataHelper.nySøknadsbehandling()
            val underkjent = testDataHelper.nyUnderkjenningMedBeregning()
            val tilAttestering = testDataHelper.nyTilAvslåttAttesteringUtenBeregning()
            testDataHelper.nyIverksattInnvilget()

            val opprettetRevurdering =
                testDataHelper.nyRevurdering(
                    testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first,
                    stønadsperiode.periode,
                )
            val tilAttesteringRevurdering = testDataHelper.tilAttesteringRevurdering()
            val underkjentRevurdering = testDataHelper.underkjentRevurdering()
            testDataHelper.tilIverksattRevurdering()

            val alleRestanser = repo.hentSakRestanser()

            alleRestanser.size shouldBe 7

            alleRestanser shouldContainExactlyInAnyOrder listOf(
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2021),
                    behandlingsId = sak.søknad.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.NY_SØKNAD,
                    opprettet = sak.søknad.opprettet,
                ),
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2022),
                    behandlingsId = søknadsbehandling.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    opprettet = søknadsbehandling.opprettet,
                ),
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2023),
                    behandlingsId = underkjent.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.UNDERKJENT,
                    opprettet = underkjent.opprettet,
                ),
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2024),
                    behandlingsId = tilAttestering.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.TIL_ATTESTERING,
                    opprettet = tilAttestering.opprettet,
                ),
                // Vi hopper over 1 saksnummer siden den blir lagret som en del når vi lager en revurdering gjennom
                // hjelpe funksjoner
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2026),
                    behandlingsId = opprettetRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    opprettet = opprettetRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2027),
                    behandlingsId = tilAttesteringRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.TIL_ATTESTERING,
                    opprettet = tilAttesteringRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2028),
                    behandlingsId = underkjentRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDERKJENT,
                    opprettet = underkjentRevurdering.opprettet,
                ),
            )
        }
    }
}
