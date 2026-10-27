package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.web.routes.regulering.omregning.DryRunOmregningBody
import org.junit.jupiter.api.Test

class DryRunInputValideringTest {
    private val dato = "2026-01-01"
    private val grunnbeløp = DryRunGrunnbeløp(
        virkningstidspunkt = dato,
        ikrafttredelse = dato,
        grunnbeløp = 130160,
        omregningsfaktor = "1.05",
        garantipensjonOrdinær = 224248,
        garantipensjonHøy = 242418,
    )
    private val body = DryRunReguleringBody(
        startDatoRegulering = "2026-01",
        gjeldendeSatsFra = dato,
        dryRunGrunnbeløp = grunnbeløp,
    )

    @Test
    fun `datoer og omregningsfaktor avvises med bad request`() {
        val ugyldigDato = "2026-02-30"
        listOf(
            body.copy(gjeldendeSatsFra = ugyldigDato) to "ugyldig_dato",
            body.copy(dryRunGrunnbeløp = grunnbeløp.copy(virkningstidspunkt = ugyldigDato)) to "ugyldig_dato",
            body.copy(dryRunGrunnbeløp = grunnbeløp.copy(ikrafttredelse = ugyldigDato)) to "ugyldig_dato",
            body.copy(dryRunGrunnbeløp = grunnbeløp.copy(omregningsfaktor = "ugyldig")) to "ugyldig_omregningsfaktor",
        ).forEach { (input, forventetKode) ->
            val resultat = input.toCommand()
            resultat.mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
            resultat.mapLeft { deserialize<ErrorJson>(it.json).code } shouldBe forventetKode.left()
        }
    }

    @Test
    fun `ugyldig sakstype blir ikke tolket som fravær av sakstypefilter`() {
        val ugyldigSakstype = "ukjent"
        val resultater = listOf(
            body.copy(kunSakstype = ugyldigSakstype).toCommand(),
            DryRunOmregningBody(fraOgMedMåned = "2026-01", sakstype = ugyldigSakstype).toCommand(),
        )
        resultater.forEach { resultat ->
            resultat.mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
            resultat.mapLeft { deserialize<ErrorJson>(it.json).code } shouldBe "ugyldig_sakstype".left()
        }
    }

    @Test
    fun `gyldig sakstype og fravær av valgfrie verdier støttes fortsatt`() {
        Sakstype.entries.forEach { sakstype ->
            body.copy(kunSakstype = sakstype.value).toCommand().map { it.kunSakstype } shouldBe
                sakstype.right()
            DryRunOmregningBody(fraOgMedMåned = "2026-01", sakstype = sakstype.value)
                .toCommand().map { it.sakstype } shouldBe sakstype.right()
        }
        body.copy(dryRunGrunnbeløp = null).toCommand().isRight() shouldBe true
        body.copy(dryRunGrunnbeløp = grunnbeløp.copy(ikrafttredelse = null)).toCommand().isRight() shouldBe true
        DryRunOmregningBody(fraOgMedMåned = "2026-01").toCommand().isRight() shouldBe true
    }
}
