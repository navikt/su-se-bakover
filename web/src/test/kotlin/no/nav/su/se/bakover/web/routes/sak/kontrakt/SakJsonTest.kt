package no.nav.su.se.bakover.web.routes.sak.kontrakt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import økonomi.domain.utbetaling.Utbetalinger
import java.util.UUID
import kotlin.random.Random

@Frontendkontrakt
internal class SakJsonTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Random.nextLong(2021, Long.MAX_VALUE)
    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(saksnummer),
        opprettet = fixedTidspunkt,
        fnr = Fnr("12345678910"),
        utbetalinger = Utbetalinger(),
        type = Sakstype.UFØRE,
        versjon = Hendelsesversjon(1),
        uteståendeKravgrunnlag = null,
    )

    //language=JSON
    private val sakJsonString =
        """
            {
                "id": "$sakId",
                "saksnummer": $saksnummer,
                "fnr": "12345678910",
                "søknader": [],
                "behandlinger" : [],
                "utbetalinger": [],
                "utbetalingerKanStansesEllerGjenopptas": "INGEN",
                "revurderinger": [],
                "vedtak": [],
                "klager": [],
                "reguleringer": [],
                "sakstype": "uføre",
                "vedtakPåTidslinje": [],
                "utenlandsopphold": {"utenlandsopphold": [], "antallDager":  0},
                "versjon": 1,
                "tilbakekrevinger": [],
                "uteståendeKravgrunnlag": null
            }
        """.trimIndent()

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(sakJsonString, serialize(sak.toJson(fixedClock, formuegrenserFactoryTestPåDato())), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SakJson>(sakJsonString) shouldBe sak.toJson(fixedClock, formuegrenserFactoryTestPåDato())
    }
}
