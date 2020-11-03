package no.nav.su.se.bakover.web.routes.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class SimuleringJsonTest {
    private val id = UUID30.randomUUID()
    private val opprettet = now()
    private val FNR = Fnr("12345678910")

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(expectedJson, serialize(simulering.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SimuleringJson>(expectedJson) shouldBe simulering.toJson()
    }

    //language=JSON
    private val expectedJson =
        """
        {
            "totalBruttoYtelse" : 41274,
            "perioder" : [
                {
                  "fraOgMed" : "2020-01-01",
                  "tilOgMed" : "2020-01-31",
                  "bruttoYtelse" : 20637
                },
                {
                  "fraOgMed" : "2020-02-01",
                  "tilOgMed" : "2020-02-28",
                  "bruttoYtelse": 20637
                }
            ]
        }
        """.trimIndent()

    private val simulering = Simulering(
        gjelderId = FNR,
        gjelderNavn = "gjelder",
        datoBeregnet = idag(),
        nettoBeløp = 123828.0,
        periodeList = listOf(
            SimulertPeriode(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.januar(2020),
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = UUID30.randomUUID().toString(),
                        feilkonto = false,
                        forfall = idag(),
                        utbetalesTilId = FNR,
                        utbetalesTilNavn = "gjelder",
                        detaljer = listOf(
                            SimulertDetaljer(
                                faktiskFraOgMed = 1.januar(2020),
                                faktiskTilOgMed = 31.januar(2020),
                                konto = "4952000",
                                belop = 20637.0,
                                tilbakeforing = false,
                                sats = 20637.0,
                                typeSats = "MND",
                                antallSats = 1,
                                uforegrad = 0,
                                klassekode = "SUUFORE",
                                klassekodeBeskrivelse = "Supplerende stønad Uføre",
                                klasseType = KlasseType.YTEL
                            )
                        )
                    )
                )
            ),
            SimulertPeriode(
                fraOgMed = 1.februar(2020),
                tilOgMed = 28.februar(2020),
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = UUID30.randomUUID().toString(),
                        feilkonto = false,
                        forfall = idag(),
                        utbetalesTilId = FNR,
                        utbetalesTilNavn = "gjelder",
                        detaljer = listOf(
                            SimulertDetaljer(
                                faktiskFraOgMed = 1.februar(2020),
                                faktiskTilOgMed = 28.februar(2020),
                                konto = "4952000",
                                belop = 20637.0,
                                tilbakeforing = false,
                                sats = 20637.0,
                                typeSats = "MND",
                                antallSats = 1,
                                uforegrad = 0,
                                klassekode = "SUUFORE",
                                klassekodeBeskrivelse = "Supplerende stønad Uføre",
                                klasseType = KlasseType.YTEL
                            )
                        )
                    )
                )
            )
        )
    )
}
