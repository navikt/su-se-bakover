package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class StønadsstatistikkMapperTest {
    val clock = fixedClock
    val aktørId = AktørId("293829399")

    @Test
    fun `mapper riktig`() {
        val bruttosats1 = listOf("2021-01-01", "2021-02-01", "2021-03-01", "2021-04-01")
        val bruttosats2 = listOf(
            "2021-05-01",
            "2021-06-01",
            "2021-07-01",
            "2021-08-01",
            "2021-09-01",
            "2021-10-01",
            "2021-11-01",
            "2021-12-01",
        )

        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            beregning = BeregningFactory.ny(
                fradragStrategy = FradragStrategy.Enslig,
                periode = periode2021,
                sats = Sats.HØY,
                fradrag = FradragFactory.periodiser(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 3000.0,
                        periode = periode2021,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        StønadsstatistikkMapper(clock).map(vedtak, aktørId, periode2021.fraOgMed) shouldBe
            Statistikk.Stønad(
                funksjonellTid = Tidspunkt.now(fixedClock),
                tekniskTid = Tidspunkt.now(fixedClock),
                stonadstype = Statistikk.Stønad.Stønadstype.SU_UFØR,
                sakId = sak.id,
                aktorId = aktørId.toString().toLong(),
                sakstype = Statistikk.Stønad.Vedtakstype.SØKNAD,
                vedtaksdato = vedtak.opprettet.toLocalDate(zoneIdOslo),
                vedtakstype = Statistikk.Stønad.Vedtakstype.SØKNAD,
                vedtaksresultat = Statistikk.Stønad.Vedtaksresultat.INNVILGET,
                behandlendeEnhetKode = "4815",
                ytelseVirkningstidspunkt = periode2021.fraOgMed,
                gjeldendeStonadVirkningstidspunkt = periode2021.fraOgMed,
                gjeldendeStonadStopptidspunkt = periode2021.tilOgMed,
                gjeldendeStonadUtbetalingsstart = periode2021.fraOgMed,
                gjeldendeStonadUtbetalingsstopp = periode2021.tilOgMed,
                månedsbeløp = bruttosats1.map {
                    Statistikk.Stønad.Månedsbeløp(
                        måned = it,
                        stonadsklassifisering = Statistikk.Stønad.Stønadsklassifisering.BOR_ALENE,
                        bruttosats = 20945,
                        nettosats = 17946,
                        inntekter = listOf(
                            Statistikk.Inntekt(
                                inntektstype = "ForventetInntekt",
                                beløp = 3000,
                            ),
                        ),
                        fradragSum = 3000,
                    )
                } + bruttosats2.map {
                    Statistikk.Stønad.Månedsbeløp(
                        måned = it,
                        stonadsklassifisering = Statistikk.Stønad.Stønadsklassifisering.BOR_ALENE,
                        bruttosats = 21989,
                        nettosats = 18989,
                        inntekter = listOf(
                            Statistikk.Inntekt(
                                inntektstype = "ForventetInntekt",
                                beløp = 3000,
                            ),
                        ),
                        fradragSum = 3000,
                    )
                },
                versjon = clock.millis(),
                opphorsgrunn = null,
                opphorsdato = null,
                flyktningsstatus = "FLYKTNING",
            )
    }

    @Test
    fun `serialiserer riktig`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            beregning = BeregningFactory.ny(
                fradragStrategy = FradragStrategy.Enslig,
                periode = periode2021,
                sats = Sats.HØY,
                fradrag = FradragFactory.periodiser(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 3000.0,
                        periode = periode2021,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )
        val actual = objectMapper.writeValueAsString(StønadsstatistikkMapper(clock).map(vedtak, aktørId, vedtak.periode.fraOgMed))
        val expected = """
                {
                  "funksjonellTid": "2021-01-01T01:02:03.456789Z",
                  "tekniskTid": "2021-01-01T01:02:03.456789Z",
                  "stonadstype": "SU_UFØR",
                  "sakId": "${sak.id}",
                  "aktorId": 293829399,
                  "sakstype": "SØKNAD",
                  "vedtaksdato": "2021-01-01",
                  "vedtakstype": "SØKNAD",
                  "vedtaksresultat": "INNVILGET",
                  "behandlendeEnhetKode": "4815",
                  "ytelseVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadStopptidspunkt": "2021-12-31",
                  "gjeldendeStonadUtbetalingsstart": "2021-01-01",
                  "gjeldendeStonadUtbetalingsstopp": "2021-12-31",
                  "månedsbeløp": [
                    {
                      "måned": "2021-01-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20945,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-02-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20945,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-03-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20945,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-04-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20945,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-05-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-06-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-07-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-08-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-09-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-10-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-11-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-12-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 21989,
                      "nettosats": 18989,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000
                        }
                      ],
                      "fradragSum": 3000
                    }
                  ],
                  "versjon": 1609462923456,
                  "flyktningsstatus": "FLYKTNING"
                }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }
}
