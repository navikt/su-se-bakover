package no.nav.su.se.bakover.domain.søknadinnhold

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class OppholdstillatelseTest {

    @Test
    fun `kan opprette oppholdstilattelse når statsborger er true, statsborgerAndreLand er false, og resten er null`() {
        Oppholdstillatelse.tryCreate(
            erNorskStatsborger = true,
            harOppholdstillatelse = null,
            oppholdstillatelseType = null,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null,
        ).shouldBeRight()
    }

    @Test
    fun `harOppholdstillatelse er påkrevd dersom norsk statsobrger er false`() {
        Oppholdstillatelse.tryCreate(
            erNorskStatsborger = false,
            harOppholdstillatelse = null,
            oppholdstillatelseType = null,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null,
        ) shouldBe FeilVedOpprettelseAvOppholdstillatelse.OppholdstillatelseErIkkeUtfylt.left()

        Oppholdstillatelse.tryCreate(
            erNorskStatsborger = false,
            harOppholdstillatelse = false,
            oppholdstillatelseType = null,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null,
        ).shouldBeRight()
    }

    @Test
    fun `type oppholdstillatelse er påkrevd dersom harOppholdstillatelse er true`() {
        Oppholdstillatelse.tryCreate(
            erNorskStatsborger = false,
            harOppholdstillatelse = true,
            oppholdstillatelseType = Oppholdstillatelse.OppholdstillatelseType.PERMANENT,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null,
        ).shouldBeRight()

        Oppholdstillatelse.tryCreate(
            erNorskStatsborger = false,
            harOppholdstillatelse = true,
            oppholdstillatelseType = null,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null,
        ) shouldBe FeilVedOpprettelseAvOppholdstillatelse.TypeOppholdstillatelseErIkkeUtfylt.left()
    }

    @Test
    fun `statsborgerskapAndreLandFritekst er påkrevd dersom statsborgerskapAndreLand er true`() {
        Oppholdstillatelse.tryCreate(
            erNorskStatsborger = true,
            harOppholdstillatelse = null,
            oppholdstillatelseType = null,
            statsborgerskapAndreLand = true,
            statsborgerskapAndreLandFritekst = "jeg er påkrevd fordi statsborgerskapAndreLand er true",
        ).shouldBeRight()

        Oppholdstillatelse.tryCreate(
            erNorskStatsborger = true,
            harOppholdstillatelse = null,
            oppholdstillatelseType = null,
            statsborgerskapAndreLand = true,
            statsborgerskapAndreLandFritekst = null,
        ) shouldBe FeilVedOpprettelseAvOppholdstillatelse.FritekstForStatsborgerskapErIkkeUtfylt.left()
    }
}
