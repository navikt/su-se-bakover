package tilbakekreving.domain.kravgrunnlag

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class KravgrunnlagTest {
    @Test
    fun `ytelsesskatt kan ikke være negativ`() {
        shouldThrow<IllegalArgumentException> {
            kravgrunnlag(
                grunnlagsperioder = nonEmptyListOf(
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = januar(2021),
                        betaltSkattForYtelsesgruppen = 0,
                        bruttoTidligereUtbetalt = 24515,
                        bruttoNyUtbetaling = 3997,
                        bruttoFeilutbetaling = 20518,
                        skatteProsent = BigDecimal("-0.0001"),
                    ),
                ),
                kravgrunnlagPåSakHendelseId = HendelseId.generer(),
            )
        }.message shouldBe "Forventer at kravgrunnlag.skatteProsent >= 0, men var -0.0001"
    }

    @Test
    fun `trekk kan forklare at brutto feilutbetaling er lavere enn differansen mellom tidligere og ny ytelse`() {
        val grunnlagsperiode = Kravgrunnlag.Grunnlagsperiode(
            periode = januar(2021),
            betaltSkattForYtelsesgruppen = 0,
            bruttoTidligereUtbetalt = 28778,
            bruttoNyUtbetaling = 0,
            bruttoFeilutbetaling = 28248,
            skatteProsent = BigDecimal.ZERO,
            trekk = listOf(
                Kravgrunnlag.Grunnlagsperiode.Trekk(
                    kodeKlasse = "KREDKRED",
                    beløpOpprinnelig = -530,
                    beløpNytt = 0,
                    beløpTilbakekreves = 0,
                    beløpUinnkrevd = 0,
                    skatteProsent = BigDecimal.ZERO,
                ),
            ),
        )

        grunnlagsperiode.trekkjusteringAvBruttoFeilutbetaling shouldBe -530
    }

    @Test
    fun `trekk må forklare hele avviket mellom ytelse og feilutbetaling`() {
        shouldThrow<IllegalArgumentException> {
            Kravgrunnlag.Grunnlagsperiode(
                periode = januar(2021),
                betaltSkattForYtelsesgruppen = 0,
                bruttoTidligereUtbetalt = 28778,
                bruttoNyUtbetaling = 0,
                bruttoFeilutbetaling = 28248,
                skatteProsent = BigDecimal.ZERO,
                trekk = emptyList(),
            )
        }.message shouldBe "Forventet at bruttoTidligereUtbetalt(28778) + summertTrekkOpprinnelig(0) - bruttoNyUtbetaling(0) - summertTrekkNytt(0) == bruttoFeilutbetaling(28248)"
    }

    @Test
    fun `kravgrunnlag med likt innhold skal være lik `() {
        val utbetalingsId = UUID30.randomUUID()
        val hendelseId = HendelseId.generer()
        val k1 = kravgrunnlag(
            kravgrunnlagPåSakHendelseId = HendelseId.fromString(hendelseId.toString()),
            utbetalingId = UUID30.fromString(utbetalingsId.toString()),
        )
        val k2 = kravgrunnlag(
            kravgrunnlagPåSakHendelseId = HendelseId.fromString(hendelseId.toString()),
            utbetalingId = UUID30.fromString(utbetalingsId.toString()),
        )
        (k1 == k2) shouldBe true
    }
}
