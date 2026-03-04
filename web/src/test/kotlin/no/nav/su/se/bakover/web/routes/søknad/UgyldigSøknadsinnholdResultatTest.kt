package no.nav.su.se.bakover.web.routes.søknad

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigSøknadsinnholdInput
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

        val body = deserialize<UgyldigSøknadsinnholdFeilResponse>(resultat.json)
        body shouldBe UgyldigSøknadsinnholdFeilResponse(
            message = "Ugyldig søknadsinnhold",
            code = UGYLDIG_SOKNADSINNHOLD_INPUT_CODE,
            errors = listOf(
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "formue.eiendomBrukesTil",
                    begrunnelse = "inneholder mistenkelig innhold",
                    code = UGYLDIG_SOKNADSINNHOLD_INPUT_CODE,
                ),
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "inntektOgPensjon.andreYtelserINav",
                    begrunnelse = "inneholder kontrolltegn",
                    code = UGYLDIG_SOKNADSINNHOLD_INPUT_CODE,
                ),
            ),
        )
    }

    private data class UgyldigSøknadsinnholdFeilResponse(
        val message: String,
        val code: String,
        val errors: List<UgyldigSøknadsinnholdValideringsfeilResponse>,
    )

    private data class UgyldigSøknadsinnholdValideringsfeilResponse(
        val felt: String,
        val begrunnelse: String,
        val code: String,
    )
}
