package no.nav.su.se.bakover.web.regulering

import common.presentation.beregning.FradragRequestJson
import common.presentation.grunnlag.UføregrunnlagJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.routes.regulering.BeregnReguleringRequest
import no.nav.su.se.bakover.web.routes.regulering.UnderkjennReguleringBody
import no.nav.su.se.bakover.web.routes.regulering.json.ManuellReguleringVisningJson

const val ATTESTANT = "Z990Attestant"

internal fun hentRegulering(reguleringsId: String, client: HttpClient): ManuellReguleringVisningJson {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        val result = defaultRequest(
            HttpMethod.Get,
            "/reguleringer/manuell/$reguleringsId",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            correlationId = correlationId.toString(),
        )
        result.apply {
            withClue("manuell reglering feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
        deserialize<ManuellReguleringVisningJson>(result.bodyAsText())
    }
}

internal fun beregnRegulering(
    reguleringsId: String,
    oppdatertUføre: List<UføregrunnlagJson>,
    oppdatertFradrag: List<FradragRequestJson>,
    client: HttpClient,
) {
    val request = BeregnReguleringRequest(
        uføre = oppdatertUføre,
        fradrag = oppdatertFradrag,
    )
    return runBlocking {
        val correlationId = CorrelationId.generate()
        defaultRequest(
            HttpMethod.Post,
            "/reguleringer/manuell/$reguleringsId/beregn",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody(serialize(request)) }.apply {
            withClue("manuell reglering feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}

internal fun reguleringTilAttestering(reguleringsId: String, client: HttpClient) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        val result = defaultRequest(
            HttpMethod.Post,
            "/reguleringer/manuell/$reguleringsId/attestering",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            correlationId = correlationId.toString(),
        )
        result.apply {
            withClue("manuell reglering feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}

internal fun underkjennRegulering(reguleringsId: String, client: HttpClient) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        val result = defaultRequest(
            HttpMethod.Post,
            "/reguleringer/manuell/$reguleringsId/attestering/underkjenn",
            listOf(Brukerrolle.Attestant),
            client = client,
            correlationId = correlationId.toString(),
            navIdent = ATTESTANT,
        ) {
            setBody(serialize(UnderkjennReguleringBody("Kommentar")))
        }
        result.apply {
            withClue("manuell reglering feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}

internal fun iverksettRegulering(reguleringsId: String, client: HttpClient) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        val result = defaultRequest(
            HttpMethod.Post,
            "/reguleringer/manuell/$reguleringsId/attestering/godkjenn",
            listOf(Brukerrolle.Attestant),
            client = client,
            correlationId = correlationId.toString(),
            navIdent = ATTESTANT,
        )
        result.apply {
            withClue("manuell reglering feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}
