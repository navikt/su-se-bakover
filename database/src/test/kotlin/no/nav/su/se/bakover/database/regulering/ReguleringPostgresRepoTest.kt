package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.persistertVariant
import no.nav.su.se.bakover.database.withMigratedDb
import org.junit.jupiter.api.Test

internal class ReguleringPostgresRepoTest {
    @Test
    fun `hent reguleringer som ikke er iverksatt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val regulering = testDataHelper.persisterReguleringOpprettet()
            testDataHelper.persisterReguleringIverksatt()

            val hentRegulering = repo.hentReguleringerSomIkkeErIverksatt()

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe regulering.persistertVariant()
        }
    }

    @Test
    fun `hent reguleringer for en sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val regulering = testDataHelper.persisterReguleringOpprettet()
            val hentRegulering = repo.hentForSakId(regulering.sakId)

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe regulering.persistertVariant()
        }
    }

    @Test
    fun `lagre og hent en opprettet regulering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val regulering = testDataHelper.persisterReguleringOpprettet()
            val hentRegulering = repo.hent(regulering.id)

            hentRegulering shouldBe regulering.persistertVariant()
        }
    }

    @Test
    fun `lagre og hent en iverksatt regulering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val regulering = testDataHelper.persisterReguleringIverksatt()
            val hentRegulering = repo.hent(regulering.id)

            hentRegulering shouldBe regulering.persistertVariant()
        }
    }

    @Test
    fun `henter saker med åpen behandling eller stans`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            // En del caser som ikke skal være åpne
            testDataHelper.persisterVedtakMedAvslåttSøknadsbehandlingUtenBeregning()
            testDataHelper.persisterVedtakForGjenopptak()
            testDataHelper.persisterVedtakForKlageIverksattAvvist()
            testDataHelper.persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering()
            testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering()
            testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingUtenKvittering()
            testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()
            testDataHelper.persisterSøknadsbehandlingIverksattAvslagUtenBeregning()
            testDataHelper.persisterSøknadsbehandlingAvsluttet()
            testDataHelper.persisterRevurderingAvsluttet()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertAvslag()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()

            val expected = listOf(
                testDataHelper.persisterVedtakForStans().behandling.saksnummer,
                testDataHelper.persisterSøknadsbehandlingBeregnetAvslag().first.saksnummer,
                testDataHelper.persisterSøknadsbehandlingBeregnetInnvilget().first.saksnummer,
                testDataHelper.persisterSøknadsbehandlingSimulert().first.saksnummer,
                testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning().first.saksnummer,
                testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning().first.saksnummer,
                testDataHelper.persisterSøknadsbehandlingTilAttesteringInnvilget().first.saksnummer,
                testDataHelper.persisterRevurderingBeregnetInnvilget().saksnummer,
                testDataHelper.persisterRevurderingBeregnetOpphørt().saksnummer,
                testDataHelper.persisterRevurderingOpprettet().saksnummer,
                testDataHelper.persisterRevurderingSimulertInnvilget().saksnummer,
                testDataHelper.persisterRevurderingSimulertOpphørt().saksnummer,
                testDataHelper.persisterRevurderingTilAttesteringInnvilget().saksnummer,
                testDataHelper.persisterRevurderingTilAttesteringOpphørt().saksnummer,
                testDataHelper.persisterRevurderingUnderkjentInnvilget().saksnummer,
                testDataHelper.persisterStansAvYtelseSimulert().saksnummer,
                testDataHelper.persisterGjenopptakAvYtelseSimulert().saksnummer,
            )
            repo.hentSakerMedÅpenBehandlingEllerStans().sortedBy { it.nummer } shouldBe expected
        }
    }
}
