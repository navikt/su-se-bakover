package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.satser.Faktor
import no.nav.su.se.bakover.domain.satser.FullSupplerendeStønadForMåned
import no.nav.su.se.bakover.domain.satser.MinsteÅrligYtelseForUføretrygdedeForMåned
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class ReguleringPostgresRepoTest {
    @Test
    fun `hent reguleringer som ikke er iverksatt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet()
            testDataHelper.persisterReguleringIverksatt()

            val hentRegulering = repo.hentReguleringerSomIkkeErIverksatt()

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe regulering
        }
    }

    @Test
    fun `hent reguleringer for en sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet()
            val hentRegulering = repo.hentForSakId(regulering.sakId)

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe regulering
        }
    }

    @Test
    fun `lagre og hent en opprettet regulering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet()
            val hentRegulering = repo.hent(regulering.id)

            hentRegulering shouldBe regulering
        }
    }

    @Test
    fun `lagre og hent en iverksatt regulering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringIverksatt()
            val hentRegulering = repo.hent(regulering.id)

            hentRegulering shouldBe regulering
        }
    }

    @Test
    fun `lagre og hent en avsluttet regulering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet()
            val avsluttetRegulering = Regulering.AvsluttetRegulering(regulering, fixedTidspunkt)

            repo.lagre(avsluttetRegulering)
            repo.hent(avsluttetRegulering.id) shouldBe avsluttetRegulering
        }
    }

    @Test
    fun `henter saker med åpen behandling eller stans`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            // En del caser som ikke skal være åpne
            testDataHelper.persisterVedtakForGjenopptak()
            testDataHelper.persisterVedtakForKlageIverksattAvvist()
            testDataHelper.persisterVedtakMedInnvilgetRevurderingOgOversendtUtbetalingMedKvittering()
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
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
                testDataHelper.persisterBeregnetRevurdering().second.saksnummer,
                testDataHelper.persisterRevurderingBeregnetOpphørt().second.saksnummer,
                testDataHelper.persisterRevurderingOpprettet().second.saksnummer,
                testDataHelper.persisterRevurderingSimulertInnvilget().second.saksnummer,
                testDataHelper.persisterRevurderingSimulertOpphørt().second.saksnummer,
                testDataHelper.persisterRevurderingTilAttesteringInnvilget().second.saksnummer,
                testDataHelper.persisterRevurderingTilAttesteringOpphørt().second.saksnummer,
                testDataHelper.persisterRevurderingUnderkjentInnvilget().second.saksnummer,
                testDataHelper.persisterSimulertStansAvYtelse().second.saksnummer,
                testDataHelper.persisterGjenopptakAvYtelseSimulert().saksnummer,
            )
            repo.hentSakerMedÅpenBehandlingEllerStans().sortedBy { it.nummer } shouldBe expected
        }
    }

    @Test
    fun `Bruker opprettet-tidspunkt for å avgjøre satser`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = fixedClock)
            val repo = testDataHelper.reguleringRepo
            val (_, regulering) = testDataHelper.persisterReguleringIverksatt()
            val hentRegulering = repo.hent(regulering.id)

            hentRegulering shouldBe regulering
            val beregning = hentRegulering!!.beregning as BeregningMedFradragBeregnetMånedsvis
            beregning.getMånedsberegninger().zip((mai(2021)..desember(2021)).måneder()) { a, b ->
                a.fullSupplerendeStønadForMåned shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = b,
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = b,
                        grunnbeløpPerÅr = 101351,
                        ikrafttredelse = 4.september(2020),
                        virkningstidspunkt = 1.mai(2020),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(value = 2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        virkningstidspunkt = 1.januar(2015),
                        måned = b,
                    ),
                    toProsentAvHøyForMåned = BigDecimal("418.9174666666666666666666666666667"),
                )
            }
        }
    }
}
