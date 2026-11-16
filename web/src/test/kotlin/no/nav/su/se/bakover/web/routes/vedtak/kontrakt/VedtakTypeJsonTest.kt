package no.nav.su.se.bakover.web.routes.vedtak.kontrakt

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.vedtak.VedtakTypeJson
import org.junit.jupiter.api.Test

@Frontendkontrakt
internal class VedtakTypeJsonTest {

    private val vedtakstyperFrontendLeser = listOf(
        "SØKNAD",
        "AVSLAG",
        "ENDRING",
        "REGULERING",
        "OPPHØR",
        "STANS_AV_YTELSE",
        "GJENOPPTAK_AV_YTELSE",
        "AVVIST_KLAGE",
        "TILBAKEKREVING",
    )

    // VedtakJson.type bygges med toString().
    @Test
    fun `type i vedtaket`() {
        VedtakTypeJson.entries.map { it.toString() } shouldContainExactlyInAnyOrder vedtakstyperFrontendLeser
    }

    // VedtakPåTidslinjeJson.vedtakType serialiseres av Jackson med enumnavnet.
    @Test
    fun `vedtakType på tidslinjen`() {
        deserialize<List<String>>(serialize(VedtakTypeJson.entries)) shouldContainExactlyInAnyOrder vedtakstyperFrontendLeser
    }
}
