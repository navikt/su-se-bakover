package no.nav.su.se.bakover.web.tilbakekreving

import common.presentation.attestering.AttesteringJson
import common.presentation.attestering.UnderkjennelseJson
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
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.domain.underkjennelse.UnderkjennAttesteringsgrunnTilbakekreving
import tilbakekreving.presentation.api.common.ForhåndsvarselMetaInfoJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus
import tilbakekreving.presentation.api.common.VurderingerMedKravJson

internal fun AppComponents.underkjennTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    saksversjon: Long,
    brevtekst: String?,
    verifiserForhåndsvarselDokumenter: List<ForhåndsvarselMetaInfoJson>,
    verifiserVurderinger: VurderingerMedKravJson,
    kommentar: String = "Underkjent av underkjennTilbakekrevingsbehandling() - TilbakekrevingsbehandlingIT",
    grunn: UnderkjennAttesteringsgrunnTilbakekreving = UnderkjennAttesteringsgrunnTilbakekreving.VURDERINGEN_ER_FEIL,
): UnderkjennTilbakekrevingsbehandlingRespons {
    val appComponents = this
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/underkjenn",
            listOf(Brukerrolle.Attestant),
            client = client,
        ) {
            setBody(
                //language=json
                """
                {
                    "versjon": $saksversjon,
                    "kommentar": "$kommentar",
                    "grunn": "$grunn"
                }
                """.trimIndent(),
            )
        }.apply {
            withClue("Kunne ikke underkjenne tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let { responseJson ->
            if (utførSideeffekter) {
                // Vi kjører konsumentene 2 ganger, for å se at vi ikke oppretter duplikate oppgaver.
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOppdatertOppgaveHendelser = 1,
                )
                // Vi sletter statusen på jobben, men ikke selve oppgavehendelsen for å verifisere at vi ikke oppretter duplikate oppgaver i disse tilfellene.
                appComponents.slettOppdatertOppgaveKonsumentJobb()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOppdatertOppgaveHendelser = 1,
                )
            }
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)
            if (utførSideeffekter) {
                // oppgavehendelse
                saksversjonEtter shouldBe saksversjon + 2
            } else {
                saksversjonEtter shouldBe saksversjon + 1
            }
            sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
            if (verifiserRespons) {
                listOf(
                    tilbakekrevingRespons,
                    deserialize(JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString()),
                ).forEach {
                    it.shouldBeEqualToIgnoringFields(
                        lagOpprettTilbakekrevingRespons(
                            sakId,
                            Tidspunkt.now(fixedClock),
                            saksversjon + 1,
                            status = TilbakekrevingsbehandlingStatus.VEDTAKSBREV,
                            fritekst = brevtekst,
                            notat = "notatet",
                        ),
                        it::id,
                        it::opprettet,
                        it::kravgrunnlag,
                        it::forhåndsvarselsInfo,
                        it::vurderinger,
                        it::attesteringer,
                    )
                    it.forhåndsvarselsInfo shouldBe verifiserForhåndsvarselDokumenter
                    it.vurderinger shouldBe verifiserVurderinger
                    it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                        lagKravgrunnlagRespons(),
                        it.kravgrunnlag!!::hendelseId,
                        it.kravgrunnlag!!::kontrollfelt,
                    )
                    it.attesteringer.size shouldBe 1
                    it.attesteringer.single().let {
                        it.shouldBeEqualToIgnoringFields(
                            lagUnderkjentAttesteringJson(
                                attestant = DEFAULT_IDENT,
                                underkjennelse = UnderkjennelseJson(grunn.toString(), kommentar),
                            ),
                            it::opprettet,
                        )
                    }
                }
            }
            UnderkjennTilbakekrevingsbehandlingRespons(
                underkjentAttestering = tilbakekrevingRespons.attesteringer,
                saksversjon = saksversjonEtter,
            )
        }
    }
}

internal data class UnderkjennTilbakekrevingsbehandlingRespons(
    val underkjentAttestering: List<AttesteringJson>,
    val saksversjon: Long,
)
