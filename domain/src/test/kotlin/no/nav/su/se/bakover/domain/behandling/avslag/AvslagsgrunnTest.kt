package no.nav.su.se.bakover.domain.behandling.avslag

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class AvslagsgrunnTest {
    @Test
    fun `mapper avslagsgrunn til opphørsgrunn`() {
        Avslagsgrunn.UFØRHET.tilOpphørsgrunn() shouldBe Opphørsgrunn.UFØRHET
        Avslagsgrunn.FOR_HØY_INNTEKT.tilOpphørsgrunn() shouldBe Opphørsgrunn.FOR_HØY_INNTEKT
        Avslagsgrunn.FORMUE.tilOpphørsgrunn() shouldBe Opphørsgrunn.FORMUE
        Avslagsgrunn.SU_UNDER_MINSTEGRENSE.tilOpphørsgrunn() shouldBe Opphørsgrunn.SU_UNDER_MINSTEGRENSE
        Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER.tilOpphørsgrunn() shouldBe Opphørsgrunn.UTENLANDSOPPHOLD
        Avslagsgrunn.OPPHOLDSTILLATELSE.tilOpphørsgrunn() shouldBe Opphørsgrunn.OPPHOLDSTILLATELSE
        Avslagsgrunn.FLYKTNING.tilOpphørsgrunn() shouldBe Opphørsgrunn.FLYKTNING

        listOf(
            Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON,
            Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE,
        ).forEach {
            assertThrows<NotImplementedError> {
                it.tilOpphørsgrunn()
            }
        }
    }
}
