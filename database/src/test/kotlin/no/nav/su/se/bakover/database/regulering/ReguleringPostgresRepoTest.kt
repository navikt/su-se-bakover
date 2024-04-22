package no.nav.su.se.bakover.database.regulering

import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import grunnbeløp.domain.GrunnbeløpForMåned
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringMerknad
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import satser.domain.Satskategori
import satser.domain.minsteårligytelseforuføretrygdede.MinsteÅrligYtelseForUføretrygdedeForMåned
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

internal class ReguleringPostgresRepoTest {
    @Test
    fun `hent reguleringer som ikke er iverksatt uten merknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet()
            testDataHelper.persisterReguleringIverksatt()
            regulering.copy(
                reguleringstype = Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt)),
            ).also { repo.lagre(it) }

            val hentRegulering = repo.hentStatusForÅpneManuelleReguleringer()

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe ReguleringSomKreverManuellBehandling(
                saksnummer = regulering.saksnummer,
                fnr = regulering.fnr,
                reguleringId = regulering.id,
                merknader = listOf(),
            )
        }
    }

    @Test
    fun `hent reguleringer som ikke er iverksatt med merknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet(
                søknadsbehandling = { (sak, søknad) ->
                    iverksattSøknadsbehandlingUføre(
                        clock = testDataHelper.clock,
                        sakOgSøknad = sak to søknad,
                        customGrunnlag = listOf(
                            lagFradragsgrunnlag(
                                opprettet = Tidspunkt.now(testDataHelper.clock),
                                type = Fradragstype.Fosterhjemsgodtgjørelse,
                                månedsbeløp = 1000.0,
                                periode = år(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,

                            ),
                        ),
                    )
                },
            )
            testDataHelper.persisterReguleringIverksatt()

            val hentRegulering = repo.hentStatusForÅpneManuelleReguleringer()

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe ReguleringSomKreverManuellBehandling(
                saksnummer = regulering.saksnummer,
                fnr = regulering.fnr,
                reguleringId = regulering.id,
                merknader = listOf(ReguleringMerknad.Fosterhjemsgodtgjørelse),
            )
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
    fun `lagre og hent en opprettet regulering (automatisk)`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet()
            val hentRegulering = repo.hent(regulering.id)

            hentRegulering shouldBe regulering
        }
    }

    @Test
    fun `lagre og hent en opprettet regulering (manuell)`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val (_, regulering) = testDataHelper.persisterReguleringOpprettet(
                søknadsbehandling = {
                    iverksattSøknadsbehandlingUføre(
                        clock = testDataHelper.clock,
                        sakOgSøknad = it.first to it.second,
                        customGrunnlag = listOf(
                            nyFradragsgrunnlag(
                                type = Fradragstype.Alderspensjon,
                                månedsbeløp = 1000.0,
                            ),
                        ),
                    )
                },
            )
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
            val avsluttetRegulering = AvsluttetRegulering(regulering, fixedTidspunkt, saksbehandler)

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
            testDataHelper.persisternySøknadsbehandlingMedStønadsperiode()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertAvslag()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()

            val expected = listOf(
                testDataHelper.persisterIverksattStansOgVedtak().second.behandling.saksnummer,
                testDataHelper.persisterSøknadsbehandlingBeregnetAvslag().first.saksnummer,
                testDataHelper.persisterSøknadsbehandlingBeregnetInnvilget().first.saksnummer,
                testDataHelper.persistersimulertSøknadsbehandling().first.saksnummer,
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
            val testDataHelper = TestDataHelper(dataSource = dataSource)
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
                        omregningsfaktor = BigDecimal(1.014951),
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
