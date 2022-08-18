package no.nav.su.se.bakover.statistikk.behandling.klage

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import org.junit.jupiter.api.Test

internal class ResultatBegrunnelseOpprettholdelseMapper {

    @Test
    fun `en paragraf`() {
        Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)).toResultatBegrunnelse() shouldBe "SU_PARAGRAF_3"
    }

    @Test
    fun `to paragrafer`() {
        Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_17))
            .toResultatBegrunnelse() shouldBe "SU_PARAGRAF_3,SU_PARAGRAF_17"
    }

    @Test
    fun `er sortert`() {
        Hjemler.Utfylt.create(
            nonEmptyListOf(
                Hjemmel.SU_PARAGRAF_4,
                Hjemmel.SU_PARAGRAF_17,
                Hjemmel.SU_PARAGRAF_3,
                Hjemmel.SU_PARAGRAF_5,
                Hjemmel.SU_PARAGRAF_7,
                Hjemmel.SU_PARAGRAF_6,
                Hjemmel.SU_PARAGRAF_9,
                Hjemmel.SU_PARAGRAF_8,
                Hjemmel.SU_PARAGRAF_21,
                Hjemmel.SU_PARAGRAF_10,
                Hjemmel.SU_PARAGRAF_13,
                Hjemmel.SU_PARAGRAF_11,
                Hjemmel.SU_PARAGRAF_12,
                Hjemmel.SU_PARAGRAF_18,
            ),
        )
            .toResultatBegrunnelse() shouldBe "SU_PARAGRAF_3,SU_PARAGRAF_4,SU_PARAGRAF_5,SU_PARAGRAF_6,SU_PARAGRAF_7,SU_PARAGRAF_8,SU_PARAGRAF_9,SU_PARAGRAF_10,SU_PARAGRAF_11,SU_PARAGRAF_12,SU_PARAGRAF_13,SU_PARAGRAF_17,SU_PARAGRAF_18,SU_PARAGRAF_21"
    }
}
