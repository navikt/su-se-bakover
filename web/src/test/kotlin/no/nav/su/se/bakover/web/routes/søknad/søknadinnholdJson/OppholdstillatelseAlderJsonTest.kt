package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import io.kotest.assertions.arrow.core.shouldBeRight
import org.junit.jupiter.api.Test

internal class OppholdstillatelseAlderJsonTest {
    private val oppholdstillatelseJson = OppholdstillatelseJson(
        erNorskStatsborger = true, harOppholdstillatelse = null, typeOppholdstillatelse = null,
        statsborgerskapAndreLand = false, statsborgerskapAndreLandFritekst = null,
    )

    @Test
    fun `oppholdstillatelseAlder felter trenger ikke å være utfylt dersom norsk statsborger er true`() {
        val oppholdstillatelseAlderJson = SøknadsinnholdAlderJson.OppholdstillatelseAlderJson(null, null)
        oppholdstillatelseAlderJson.toOppholdstillatelseAlder(oppholdstillatelseJson).shouldBeRight()
    }

    @Test
    fun `oppholdstillatelseAlder felter må være utfylt dersom norsk statsborger er false`() {
        val oppholdstillatlese = oppholdstillatelseJson.copy(erNorskStatsborger = false)

        val oppholdstillatelseAlderJson =
            SøknadsinnholdAlderJson.OppholdstillatelseAlderJson(eøsborger = true, familieforening = false)

        oppholdstillatelseAlderJson.toOppholdstillatelseAlder(oppholdstillatlese).shouldBeRight()
    }
}
