package no.nav.su.se.bakover.web.routes.historisk.kontrakt

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.historisk.HistoriskInfotrygdOppdragTestdata
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.historisk.HistoriskSimuleringResponse
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

@Frontendkontrakt
internal class HistoriskSimuleringJsonTest {
    @Test
    fun `resultatet beholder oppdragsreferanser og detaljer uten rå XML`() {
        val data = HistoriskInfotrygdOppdragTestdata
        val periode = data.januar
        val respons = data.simuleringsrespons(Fnr("12345678910"), periode)
        val beregning = requireNotNull(respons.beregning)
        val response = HistoriskSimuleringResponse(
            simuleringsperiode = periode.toJson(),
            simulert = data.tidspunkt.toString(),
            resultat = beregning.copy(
                perioder = beregning.perioder.map { it.copy(utbetalinger = it.utbetalinger.take(1)) },
            ),
            infomelding = respons.infomelding,
        )
        JSONAssert.assertEquals(
            """
            {
              "simuleringsperiode": {"fraOgMed":"2018-01-01","tilOgMed":"2018-01-31"},
              "simulert": "2026-11-13T10:00:00Z",
              "infomelding": "Syntetisk testrespons",
              "resultat": {
                "gjelderId": "12345678910",
                "gjelderNavn": "Testperson",
                "datoBeregnet": "2018-01-31",
                "faggruppe": "TEST_FAGGRUPPE",
                "beløp": 12000,
                "perioder": [{
                  "fraOgMed": "2018-01-01",
                  "tilOgMed": "2018-01-31",
                  "utbetalinger": [{
                    "fagområde": "UKJENT_FAGOMRAADE",
                    "stoppnivåId": "4",
                    "behandlendeEnhet": "8020",
                    "oppdragsId": "5",
                    "fagsystemId": "test-oppdrag",
                    "kid": null,
                    "utbetalesTilId": "12345678910",
                    "utbetalesTilNavn": "Testperson",
                    "bilagstype": "TEST",
                    "forfall": "2018-01-31",
                    "feilkonto": false,
                    "detaljer": [{
                      "faktiskFraOgMed": "2018-01-01",
                      "faktiskTilOgMed": "2018-01-31",
                      "konto": "TESTKONTO",
                      "behandlingskode": "TESTKODE",
                      "beløp": 12000,
                      "trekkVedtakId": "1",
                      "stønadId": null,
                      "korrigering": null,
                      "tilbakeføring": false,
                      "linjeId": "2",
                      "sats": 12000,
                      "typeSats": "MND",
                      "antallSats": 1,
                      "saksbehandlerId": "SU",
                      "uføregrad": 0,
                      "kravhaverId": null,
                      "delytelseId": "3",
                      "bostedsenhet": "8020",
                      "skyldnerId": null,
                      "klassekode": "UKJENT_KLASSEKODE",
                      "klassekodeBeskrivelse": "Ukjent klassekode skal bevares",
                      "klasseType": "UKJENT_KLASSETYPE",
                      "klasseTypeBeskrivelse": "Ukjent klassetype skal bevares",
                      "refunderesOrgNr": null
                    }]
                  }]
                }]
              }
            }
            """.trimIndent(),
            serialize(response),
            true,
        )
    }
}
