package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.presentation.api.common.KravgrunnlagStatusJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus
import tilbakekreving.presentation.api.kravgrunnlag.AnnullertKravgrunnlagJson

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
    verifiserBehandling: Boolean = false,
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

            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            val annullertKravgrunnlagRespons = deserialize<AnnullertKravgrunnlagJson>(responseJson)
            annullertKravgrunnlagRespons.uteståendeKravgrunnlag shouldBe null

            if (verifiserBehandling) {
                annullertKravgrunnlagRespons.tilbakekrevingsbehandling!!.let {
                    it.shouldBeEqualToIgnoringFields(
                        lagOpprettTilbakekrevingRespons(
                            sakId,
                            Tidspunkt.now(fixedClock),
                            saksversjon + 1,
                            TilbakekrevingsbehandlingStatus.AVBRUTT,
                        ),
                        it::id,
                        it::opprettet,
                        it::kravgrunnlag,
                        it::avsluttetTidspunkt,
                    )

                    it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                        lagKravgrunnlagRespons(
                            status = KravgrunnlagStatusJson.NY,
                        ),
                        it.kravgrunnlag!!::hendelseId,
                        it.kravgrunnlag!!::kontrollfelt,
                    )
                }

                deserialize<TilbakekrevingsbehandlingJson>(
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                ).let {
                    it.shouldBeEqualToIgnoringFields(
                        lagOpprettTilbakekrevingRespons(
                            sakId,
                            Tidspunkt.now(fixedClock),
                            saksversjon + 1,
                            TilbakekrevingsbehandlingStatus.AVBRUTT,
                        ),
                        it::id,
                        it::opprettet,
                        it::kravgrunnlag,
                        it::avsluttetTidspunkt,
                    )

                    it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                        lagKravgrunnlagRespons(
                            status = KravgrunnlagStatusJson.ANNU,
                        ),
                        it.kravgrunnlag!!::hendelseId,
                        it.kravgrunnlag!!::kontrollfelt,
                    )
                }
            }

            AnnullerKravgrunnlagResponse(
                saksversjon = saksversjonEtter,
                responseJson = responseJson,
            )
        }
    }
}

data class AnnullerKravgrunnlagResponse(
    val saksversjon: Long,
    val responseJson: String,
)
