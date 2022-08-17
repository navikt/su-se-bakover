package no.nav.su.se.bakover.service.statistikk.mappers

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import org.junit.jupiter.api.Test

internal class KlagehjemlerMapperKtTest {

    @Test
    fun `en paragraf`() {
        Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)).mapToResultatBegrunnelse() shouldBe "SU_PARAGRAF_3"
    }

    @Test
    fun `to paragrafer`() {
        Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_17))
            .mapToResultatBegrunnelse() shouldBe "SU_PARAGRAF_3,SU_PARAGRAF_17"
    }

    @Test
    fun `er sortert`() {
        Hjemler.Utfylt.create(
            nonEmptyListOf(
                Hjemmel.SU_PARAGRAF_4,
                Hjemmel.SU_PARAGRAF_17,
                Hjemmel.SU_PARAGRAF_3,
            ),
        ).mapToResultatBegrunnelse() shouldBe "SU_PARAGRAF_3,SU_PARAGRAF_4,SU_PARAGRAF_17"
    }
}
