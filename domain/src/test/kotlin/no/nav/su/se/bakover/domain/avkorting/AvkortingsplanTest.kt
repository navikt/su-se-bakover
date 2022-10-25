package no.nav.su.se.bakover.domain.avkorting

import arrow.core.left
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.application.Beløp
import no.nav.su.se.bakover.common.application.MånedBeløp
import no.nav.su.se.bakover.common.application.Månedsbeløp
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvkortingsplanTest {
    @Test
    fun `avkorter ingenting dersom det ikke finnes noe å avkorte`() {
        val (_, søknadsbehandling) = søknadsbehandlingBeregnetInnvilget()
        Avkortingsplan(
            feilutbetaltBeløp = Månedsbeløp(emptyList()).sum(),
            beregning = søknadsbehandling.beregning,
            clock = fixedClock,
        ).lagFradrag().getOrFail() shouldBe emptyList()
    }

    @Test
    fun `avkorter så mye som mulig, så fort som mulig`() {
        val (_, søknadsbehandling) = søknadsbehandlingBeregnetInnvilget()
        Avkortingsplan(
            feilutbetaltBeløp = Månedsbeløp(
                listOf(
                    MånedBeløp(
                        januar(2021),
                        Beløp(satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAvrundet),
                    ),
                    MånedBeløp(
                        februar(2021),
                        Beløp(satsFactoryTestPåDato().høyUføre(februar(2021)).satsForMånedAvrundet),
                    ),
                    MånedBeløp(mars(2021), Beløp(satsFactoryTestPåDato().høyUføre(mars(2021)).satsForMånedAvrundet)),
                ),
            ).sum(),
            beregning = søknadsbehandling.beregning,
            clock = fixedClock,
        ).lagFradrag().getOrFail().let {
            it shouldHaveSize 3
            it[0].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAvrundet,
                    periode = januar(2021),
                ),
            )
            it[1].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = satsFactoryTestPåDato().høyUføre(februar(2021)).satsForMånedAvrundet,
                    periode = februar(2021),
                ),
            )
            it[2].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = satsFactoryTestPåDato().høyUføre(mars(2021)).satsForMånedAvrundet,
                    periode = mars(2021),
                ),
            )
        }
    }

    @Test
    fun `avkorting går ikke på bekostning av vanlige fradrag`() {
        val (_, søknadsbehandling) = søknadsbehandlingBeregnetInnvilget(
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Sosialstønad,
                            månedsbeløp = satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAsDouble,
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Sosialstønad,
                            månedsbeløp = satsFactoryTestPåDato().høyUføre(mai(2021)).satsForMånedAsDouble,
                            periode = Periode.create(1.mai(2021), 30.september(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                bosituasjon = listOf(bosituasjongrunnlagEnslig(periode = år(2021))),
            ),
        )

        Avkortingsplan(
            feilutbetaltBeløp = Månedsbeløp(
                listOf(
                    MånedBeløp(januar(2021), Beløp(satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAvrundet)),
                    MånedBeløp(februar(2021), Beløp(satsFactoryTestPåDato().høyUføre(februar(2021)).satsForMånedAvrundet)),
                    MånedBeløp(mars(2021), Beløp(satsFactoryTestPåDato().høyUføre(mars(2021)).satsForMånedAvrundet)),
                ),
            ).sum(),
            beregning = søknadsbehandling.beregning,
            clock = fixedClock,
        ).lagFradrag().getOrFail().let {
            it[0].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 20946,
                    periode = oktober(2021),
                ),
            )
            it[1].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 20946,
                    periode = november(2021),
                ),
            )
            it[2].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 20946,
                    periode = desember(2021),
                ),
            )
        }
    }

    @Test
    fun `svarer med feil dersom beløp til avkorting ikke lar seg akorte fullstendig for aktuell beregning`() {
        val (_, søknadsbehandling) = søknadsbehandlingBeregnetInnvilget(
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Sosialstønad,
                            månedsbeløp = satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAsDouble,
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Sosialstønad,
                            månedsbeløp = satsFactoryTestPåDato().høyUføre(mai(2021)).satsForMånedAsDouble,
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                bosituasjon = listOf(bosituasjongrunnlagEnslig(periode = år(2021))),
            ),
        )

        Avkortingsplan(
            feilutbetaltBeløp = Månedsbeløp(
                listOf(
                    MånedBeløp(
                        januar(2021),
                        Beløp(satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAvrundet),
                    ),
                    MånedBeløp(
                        februar(2021),
                        Beløp(satsFactoryTestPåDato().høyUføre(februar(2021)).satsForMånedAvrundet),
                    ),
                    MånedBeløp(mars(2021), Beløp(satsFactoryTestPåDato().høyUføre(mars(2021)).satsForMånedAvrundet)),
                ),
            ).sum(),
            beregning = søknadsbehandling.beregning,
            clock = fixedClock,
        ).lagFradrag() shouldBe Avkortingsplan.KunneIkkeLageAvkortingsplan.AvkortingErUfullstendig.left()
    }

    @Test
    fun `forskyver avkorting til påfølgende måned dersom beløp ikke lar seg avkorte`() {
        val (_, søknadsbehandling) = søknadsbehandlingBeregnetInnvilget(
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Kapitalinntekt,
                            månedsbeløp = 10000.0,
                            periode = år(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                bosituasjon = listOf(bosituasjongrunnlagEnslig(periode = år(2021))),
            ),
        )

        Avkortingsplan(
            feilutbetaltBeløp = Månedsbeløp(
                listOf(
                    MånedBeløp(
                        januar(2021),
                        Beløp(satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAvrundet),
                    ),
                    MånedBeløp(
                        februar(2021),
                        Beløp(satsFactoryTestPåDato().høyUføre(februar(2021)).satsForMånedAvrundet),
                    ),
                    MånedBeløp(mars(2021), Beløp(satsFactoryTestPåDato().høyUføre(mars(2021)).satsForMånedAvrundet)),
                ),
            ).sum(),
            beregning = søknadsbehandling.beregning,
            clock = fixedClock,
        ).lagFradrag().getOrFail().let {
            it shouldHaveSize 6
            it[0].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 10946,
                    periode = januar(2021),
                ),
            )
            it[1].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 10946,
                    periode = februar(2021),
                ),
            )
            it[2].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 10946,
                    periode = mars(2021),
                ),
            )
            it[3].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 10946,
                    periode = april(2021),
                ),
            )
            it[4].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 10946,
                    periode = mai(2021),
                ),
            )
            it[5].shouldBeEqualToExceptId(
                expectAvkorting(
                    beløp = 8108,
                    periode = juni(2021),
                ),
            )
        }
    }

    private fun expectAvkorting(
        beløp: Int,
        periode: Periode,
    ): Grunnlag.Fradragsgrunnlag {
        return Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                månedsbeløp = beløp.toDouble(),
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }
}
