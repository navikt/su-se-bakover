package vilkår.common.domain

import behandling.revurdering.domain.Opphørsgrunn
import behandling.revurdering.domain.slåSammenForHøyInntektOgSuUnderMinstegrense
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class OpphørsgrunnTest {

    @Test
    fun `Velg FOR_HØY_INNTEKT over SU_UNDER_MINSTEGRENSE`() {
        listOf(
            Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
            Opphørsgrunn.FOR_HØY_INNTEKT,
            Opphørsgrunn.FORMUE,
        ).slåSammenForHøyInntektOgSuUnderMinstegrense() shouldBe listOf(
            Opphørsgrunn.FOR_HØY_INNTEKT,
            Opphørsgrunn.FORMUE,
        )
    }

    @Test
    fun `Beholder SU_UNDER_MINSTEGRENSE hvis vi ikke har FOR_HØY_INNTEKT`() {
        listOf(
            Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
            Opphørsgrunn.FORMUE,
        ).slåSammenForHøyInntektOgSuUnderMinstegrense() shouldBe listOf(
            Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
            Opphørsgrunn.FORMUE,
        )
    }

    @Test
    fun `Beholder FOR_HØY_INNTEKT hvis vi ikke har SU_UNDER_MINSTEGRENSE`() {
        listOf(
            Opphørsgrunn.FOR_HØY_INNTEKT,
            Opphørsgrunn.FORMUE,
        ).slåSammenForHøyInntektOgSuUnderMinstegrense() shouldBe listOf(
            Opphørsgrunn.FOR_HØY_INNTEKT,
            Opphørsgrunn.FORMUE,
        )
    }

    @Test
    fun `Filtrer ikke enkel FOR_HØY_INNTEKT`() {
        listOf(
            Opphørsgrunn.FOR_HØY_INNTEKT,
        ).slåSammenForHøyInntektOgSuUnderMinstegrense() shouldBe listOf(
            Opphørsgrunn.FOR_HØY_INNTEKT,
        )
    }

    @Test
    fun `Filtrer ikke enkel SU_UNDER_MINSTEGRENSE`() {
        listOf(
            Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
        ).slåSammenForHøyInntektOgSuUnderMinstegrense() shouldBe listOf(
            Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
        )
    }

    @Test
    fun `tom liste`() {
        emptyList<Opphørsgrunn>().slåSammenForHøyInntektOgSuUnderMinstegrense() shouldBe emptyList()
    }
}
