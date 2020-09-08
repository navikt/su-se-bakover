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
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class UtbetalingJsonTest {
    private val id = UUID30.randomUUID()
    private val opprettet = now()
    private val FNR = Fnr("12345678910")

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(expectedJson, serialize(utbetaling.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<UtbetalingJson>(expectedJson) shouldBe utbetaling.toJson()
    }

    //language=JSON
    private val expectedJson =
        """
        {
          "id": "$id",
          "opprettet": "${DateTimeFormatter.ISO_INSTANT.format(opprettet)}",
          "simulering": {
            "totalBruttoYtelse" : 41274,
            "perioder" : [
                {
                  "fom" : "2020-01-01",
                  "tom" : "2020-01-31",
                  "bruttoYtelse" : 20637
                },
                {
                  "fom" : "2020-02-01",
                  "tom" : "2020-02-28",
                  "bruttoYtelse": 20637
                }
            ]
          }
        }
        """.trimIndent()

    private val utbetaling = Utbetaling(
        id = id,
        opprettet = opprettet,
        behandlingId = UUID.randomUUID(),
        simulering = Simulering(
            gjelderId = FNR,
            gjelderNavn = "gjelder",
            datoBeregnet = idag(),
            nettoBeløp = 123828,
            periodeList = listOf(
                SimulertPeriode(
                    fom = 1.januar(2020),
                    tom = 31.januar(2020),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = UUID30.randomUUID().toString(),
                            feilkonto = false,
                            forfall = idag(),
                            utbetalesTilId = FNR,
                            utbetalesTilNavn = "gjelder",
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFom = 1.januar(2020),
                                    faktiskTom = 31.januar(2020),
                                    konto = "4952000",
                                    belop = 20637,
                                    tilbakeforing = false,
                                    sats = 20637,
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
                    fom = 1.februar(2020),
                    tom = 28.februar(2020),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = UUID30.randomUUID().toString(),
                            feilkonto = false,
                            forfall = idag(),
                            utbetalesTilId = FNR,
                            utbetalesTilNavn = "gjelder",
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFom = 1.februar(2020),
                                    faktiskTom = 28.februar(2020),
                                    konto = "4952000",
                                    belop = 20637,
                                    tilbakeforing = false,
                                    sats = 20637,
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
        ),
        utbetalingslinjer = emptyList()
    )
}
