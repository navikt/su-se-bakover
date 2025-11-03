package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.web.komponenttest.AppComponents

internal data class AnnullerKravgrunnlagTilbakekrevingsbehandlingVerifikasjon(
    val behandlingsId: String,
    val sakId: String,
    val kravgrunnlagHendelseId: String,
)

internal fun AppComponents.annullerKravgrunnlag(
    sakId: String,
    kravgrunnlagHendelseId: String,
    client: HttpClient,
    saksversjon: Long,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    verifiserBehandling: AnnullerKravgrunnlagTilbakekrevingsbehandlingVerifikasjon? = null,
): AnnullerKravgrunnlagResponse {
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            method = HttpMethod.Patch,
            uri = "/saker/$sakId/tilbakekreving/kravgrunnlag/$kravgrunnlagHendelseId/annuller",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody("""{"versjon": $saksversjon}""")
        }.apply {
            withClue("Kunne ikke annullere kravgrunnlag: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let { responseJson ->
            verifiserResponse(responseJson, verifiserBehandling)

            AnnullerKravgrunnlagResponse(
                saksversjon = saksversjon.inc(),
                responseJson = responseJson,
            )
        }
    }
}

data class AnnullerKravgrunnlagResponse(
    val saksversjon: Long,
    val responseJson: String,
)

internal fun verifiserResponse(
    actual: String,
    verifiserBehandling: AnnullerKravgrunnlagTilbakekrevingsbehandlingVerifikasjon? = null,
) {
    val expected = if (verifiserBehandling != null) {
        """{
                "id":"${verifiserBehandling.behandlingsId}",
                "sakId":"${verifiserBehandling.sakId}",
                "opprettet":"2021-02-01T01:03:53.456789Z",
                "opprettetAv":"$DEFAULT_IDENT",
                "kravgrunnlag":{
                 "hendelseId":${verifiserBehandling.kravgrunnlagHendelseId},
                 "eksternKravgrunnlagsId":"123456",
                 "eksternVedtakId":"654321",
                 "kontrollfelt":"2021-02-01-02.03.48.456789",
                 "status":"NY",
                 "grunnlagsperiode":[{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-01-31"},"betaltSkattForYtelsesgruppen":"1192","bruttoTidligereUtbetalt":"10946","bruttoNyUtbetaling":"8563","bruttoFeilutbetaling":"2383","nettoFeilutbetaling":"1191","skatteProsent":"50","skattFeilutbetaling":"1192"}],
                 "summertBetaltSkattForYtelsesgruppen":"1192",
                 "summertBruttoTidligereUtbetalt":10946,
                 "summertBruttoNyUtbetaling":8563,
                 "summertBruttoFeilutbetaling":2383,
                 "summertNettoFeilutbetaling":1191,
                 "summertSkattFeilutbetaling":1192
                },
                "status":"AVBRUTT",
                "vurderinger":null,
                "fritekst":null,
                "forhåndsvarselsInfo":[],
                "versjon":8,
                "sendtTilAttesteringAv":null,
                "attesteringer":[],
                "erKravgrunnlagUtdatert":false,
                "avsluttetTidspunkt":"2021-02-01T01:03:57.456789Z",
                "notat":null
        }
        """.trimIndent()
    } else {
        null
    }

    actual.shouldBeSimilarJsonTo(
        """{
         "uteståendeKravgrunnlag": null,
         "tilbakekrevingsbehandling": $expected
        }
        """.trimIndent(),
        "tilbakekrevingsbehandling.opprettet",
        "tilbakekrevingsbehandling.kravgrunnlag.kontrollfelt",
        "tilbakekrevingsbehandling.avsluttetTidspunkt",
    )
}
