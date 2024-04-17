package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.HistoriskSendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import java.util.UUID

internal class TilbakekrevingsbehandlingJsonTest {

    @Test
    fun `til json`() {
        val domain = HistoriskSendtTilbakekrevingsvedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            revurderingId = RevurderingId.generer(),
            periode = år(2021),
            kravgrunnlag = kravgrunnlag(),
            kravgrunnlagMottatt = fixedTidspunkt,
            tilbakekrevingsvedtakForsendelse = RåTilbakekrevingsvedtakForsendelse(
                requestXml = "requestXml",
                responseXml = "responseXml",
                tidspunkt = fixedTidspunkt,
            ),
            avgjørelse = HistoriskSendtTilbakekrevingsvedtak.AvgjørelseTilbakekrevingUnderRevurdering.Tilbakekrev,
        )

        JSONAssert.assertEquals(
            """{"avgjørelse":"TILBAKEKREV"}""",
            serialize(domain.toJson()!!),
            true,
        )

        val actual = serialize(
            domain.copy(avgjørelse = HistoriskSendtTilbakekrevingsvedtak.AvgjørelseTilbakekrevingUnderRevurdering.IkkeTilbakekrev)
                .toJson()!!,
        )
        JSONAssert.assertEquals(
            """{"avgjørelse":"IKKE_TILBAKEKREV"}""",
            actual,
            true,
        )
    }
}
