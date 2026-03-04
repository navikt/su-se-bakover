package no.nav.su.se.bakover.web.routes.søknad

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigSøknadsinnholdInput
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class UgyldigSøknadsinnholdResultatTest {

    @Test
    fun `returnerer alle inputfeil i errors-listen`() {
        val resultat = listOf(
            UgyldigSøknadsinnholdInput(
                felt = "formue.eiendomBrukesTil",
                begrunnelse = "inneholder mistenkelig innhold",
            ),
            UgyldigSøknadsinnholdInput(
                felt = "inntektOgPensjon.andreYtelserINav",
                begrunnelse = "inneholder kontrolltegn",
            ),
        ).tilUgyldigSøknadsinnholdResultat()

        resultat.httpCode shouldBe BadRequest

        val body = JSONObject(resultat.json)
        body.getString("code") shouldBe UGYLDIG_SOKNADSINNHOLD_INPUT_CODE

        val errors = body.getJSONArray("errors")
        errors.length() shouldBe 2
        errors.getJSONObject(0).getString("code") shouldBe UGYLDIG_SOKNADSINNHOLD_INPUT_CODE
        errors.getJSONObject(1).getString("code") shouldBe UGYLDIG_SOKNADSINNHOLD_INPUT_CODE
    }
}
