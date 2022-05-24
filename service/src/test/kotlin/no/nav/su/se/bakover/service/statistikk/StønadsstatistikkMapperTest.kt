package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.statistikk.mappers.StønadsstatistikkMapper
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.vedtakIverksattAutomatiskRegulering
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.temporal.ChronoUnit

internal class StønadsstatistikkMapperTest {
    private val aktørId = AktørId("293829399")
    private val testdata = vedtakSøknadsbehandlingIverksattInnvilget().let { (_, vedtak) ->
        vedtakSøknadsbehandlingIverksattInnvilget(
            vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger.copy(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = arrow.core.nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = java.util.UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = Grunnlag.Uføregrunnlag(
                                id = java.util.UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = år(2021),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = 36000,
                            ),
                            periode = år(2021),
                            begrunnelse = "innvilgetUførevilkårForventetInntekt0",
                        ),
                    ),
                ),
            ),
        )
    }
    private val sak = testdata.first
    private val vedtak = testdata.second

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

        StønadsstatistikkMapper(fixedClock).map(vedtak, aktørId, år(2021).fraOgMed, sak) shouldBe
            Statistikk.Stønad(
                funksjonellTid = fixedTidspunkt,
                tekniskTid = fixedTidspunkt,
                stonadstype = Statistikk.Stønad.Stønadstype.SU_UFØR,
                sakId = sak.id,
                aktorId = aktørId.toString().toLong(),
                sakstype = Statistikk.Stønad.Vedtakstype.SØKNAD,
                vedtaksdato = vedtak.opprettet.toLocalDate(zoneIdOslo),
                vedtakstype = Statistikk.Stønad.Vedtakstype.SØKNAD,
                vedtaksresultat = Statistikk.Stønad.Vedtaksresultat.INNVILGET,
                behandlendeEnhetKode = "4815",
                ytelseVirkningstidspunkt = år(2021).fraOgMed,
                gjeldendeStonadVirkningstidspunkt = år(2021).fraOgMed,
                gjeldendeStonadStopptidspunkt = år(2021).tilOgMed,
                gjeldendeStonadUtbetalingsstart = år(2021).fraOgMed,
                gjeldendeStonadUtbetalingsstopp = år(2021).tilOgMed,
                månedsbeløp = bruttosats1.map
                {
                    Statistikk.Stønad.Månedsbeløp(
                        måned = it,
                        stonadsklassifisering = Statistikk.Stønadsklassifisering.BOR_ALENE,
                        bruttosats = 20946,
                        nettosats = 17946,
                        inntekter = listOf(
                            Statistikk.Inntekt(
                                inntektstype = "ForventetInntekt",
                                beløp = 3000,
                                tilhører = "BRUKER",
                                erUtenlandsk = false
                            ),
                        ),
                        fradragSum = 3000,
                    )
                } + bruttosats2.map
                {
                    Statistikk.Stønad.Månedsbeløp(
                        måned = it,
                        stonadsklassifisering = Statistikk.Stønadsklassifisering.BOR_ALENE,
                        bruttosats = 20946,
                        nettosats = 17946,
                        inntekter = listOf(
                            Statistikk.Inntekt(
                                inntektstype = "ForventetInntekt",
                                beløp = 3000,
                                tilhører = "BRUKER",
                                erUtenlandsk = false
                            ),
                        ),
                        fradragSum = 3000,
                    )
                },
                versjon = fixedClock.millis(),
                opphorsgrunn = null,
                opphorsdato = null,
                flyktningsstatus = "FLYKTNING",
            )
    }

    @Test
    fun `serialiserer riktig`() {
        val actual = objectMapper.writeValueAsString(
            StønadsstatistikkMapper(fixedClock).map(
                vedtak = vedtak,
                aktørId = aktørId,
                ytelseVirkningstidspunkt = vedtak.periode.fraOgMed,
                sak = sak,
            ),
        )
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
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-02-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-03-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-04-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-05-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-06-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-07-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-08-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-09-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-10-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-11-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 3000
                    },
                    {
                      "måned": "2021-12-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 17946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 3000,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
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

    @Test
    fun `Stans gir nullutbetaling`() {
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = Periode.create(1.januar(2021), 28.februar(2021)),
        )
        val actual = objectMapper.writeValueAsString(
            StønadsstatistikkMapper(clock = fixedClock).map(
                vedtak = vedtak,
                aktørId = aktørId,
                ytelseVirkningstidspunkt = vedtak.periode.fraOgMed,
                sak = sak,
            ),
        )
        val expected = """
                {
                  "funksjonellTid": "2021-01-01T01:02:03.456789Z",
                  "tekniskTid": "2021-01-01T01:02:03.456789Z",
                  "stonadstype": "SU_UFØR",
                  "sakId": "${sak.id}",
                  "aktorId": 293829399,
                  "sakstype": "STANS",
                  "vedtaksdato": "2021-01-01",
                  "vedtakstype": "STANS",
                  "vedtaksresultat": "STANSET",
                  "behandlendeEnhetKode": "4815",
                  "ytelseVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadStopptidspunkt": "2021-02-28",
                  "gjeldendeStonadUtbetalingsstart": "2021-01-01",
                  "gjeldendeStonadUtbetalingsstopp": "2021-02-28",
                  "månedsbeløp": [],
                  "versjon": 1609462923456,
                  "flyktningsstatus": "FLYKTNING"
                }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `Gjenopptak sender med riktig månedsbeløp`() {
        val (sak, vedtak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            Periode.create(
                1.januar(2021),
                28.februar(2021),
            ),
        )
        val actual = objectMapper.writeValueAsString(
            StønadsstatistikkMapper(fixedClock).map(
                vedtak = vedtak,
                aktørId = aktørId,
                ytelseVirkningstidspunkt = vedtak.periode.fraOgMed,
                sak = sak,
            ),
        )
        val expected = """
                {
                  "funksjonellTid": "2021-01-01T01:02:03.456789Z",
                  "tekniskTid": "2021-01-01T01:02:03.456789Z",
                  "stonadstype": "SU_UFØR",
                  "sakId": "${sak.id}",
                  "aktorId": 293829399,
                  "sakstype": "GJENOPPTAK",
                  "vedtaksdato": "2021-01-01",
                  "vedtakstype": "GJENOPPTAK",
                  "vedtaksresultat": "GJENOPPTATT",
                  "behandlendeEnhetKode": "4815",
                  "ytelseVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadStopptidspunkt": "2021-02-28",
                  "gjeldendeStonadUtbetalingsstart": "2021-01-01",
                  "gjeldendeStonadUtbetalingsstopp": "2021-02-28",
                  "månedsbeløp": [
                    {
                      "måned": "2021-01-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 20946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 0,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 0
                    },
                    {
                      "måned": "2021-02-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 20946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 0,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 0
                    }
                  ],
                  "versjon": 1609462923456,
                  "flyktningsstatus": "FLYKTNING"
                }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `Gjenopptak bruker nyeste beregning for hver måned`() {
        val stønadsperiode = Periode.create(1.januar(2021), 28.februar(2021))
        var sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                stønadsperiode,
                "",
            ),
            clock = fixedClock,
        )
        lateinit var gjenopptakVedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse
        sakOgVedtak = vedtakRevurderingIverksattInnvilget(sakOgVedtakSomKanRevurderes = sakOgVedtak, revurderingsperiode = Periode.create(1.februar(2021), 28.februar(2021)), clock = fixedClock.plus(1, ChronoUnit.SECONDS))
        sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(sakOgVedtakSomKanRevurderes = sakOgVedtak, periode = stønadsperiode, clock = fixedClock.plus(2, ChronoUnit.SECONDS))
        vedtakIverksattGjenopptakAvYtelseFraIverksattStans(sakOgVedtakSomKanRevurderes = sakOgVedtak, periode = stønadsperiode, clock = fixedClock.plus(3, ChronoUnit.SECONDS)).let {
            sakOgVedtak = it
            gjenopptakVedtak = it.second
        }

        val actual = objectMapper.writeValueAsString(
            StønadsstatistikkMapper(fixedClock).map(
                vedtak = gjenopptakVedtak,
                aktørId = aktørId,
                ytelseVirkningstidspunkt = vedtak.periode.fraOgMed,
                sak = sakOgVedtak.first,
            ),
        )
        val expected = """
                {
                  "funksjonellTid": "2021-01-01T01:02:03.456789Z",
                  "tekniskTid": "2021-01-01T01:02:03.456789Z",
                  "stonadstype": "SU_UFØR",
                  "sakId": "${sak.id}",
                  "aktorId": 293829399,
                  "sakstype": "GJENOPPTAK",
                  "vedtaksdato": "2021-01-01",
                  "vedtakstype": "GJENOPPTAK",
                  "vedtaksresultat": "GJENOPPTATT",
                  "behandlendeEnhetKode": "4815",
                  "ytelseVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadStopptidspunkt": "2021-02-28",
                  "gjeldendeStonadUtbetalingsstart": "2021-01-01",
                  "gjeldendeStonadUtbetalingsstopp": "2021-02-28",
                  "månedsbeløp": [
                    {
                      "måned": "2021-01-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 20946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 0,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 0
                    },
                    {
                      "måned": "2021-02-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 13446,
                      "inntekter": [
                        {
                          "inntektstype": "Arbeidsinntekt",
                          "beløp": 7500,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 7500
                    }
                  ],
                  "versjon": 1609462923456,
                  "flyktningsstatus": "FLYKTNING"
                }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `stønadsstatistikk for regulering`() {
        val (sak, regulering) = vedtakIverksattAutomatiskRegulering(stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 28.februar(2021))))

        val actual = objectMapper.writeValueAsString(
            StønadsstatistikkMapper(fixedClock).map(
                vedtak = regulering,
                aktørId = aktørId,
                ytelseVirkningstidspunkt = sak.søknadsbehandlinger.first().periode.fraOgMed,
                sak = sak,
            ),
        )

        val expected = """
                {
                  "funksjonellTid": "2021-01-01T01:02:03.456789Z",
                  "tekniskTid": "2021-01-01T01:02:03.456789Z",
                  "stonadstype": "SU_UFØR",
                  "sakId": "${sak.id}",
                  "aktorId": 293829399,
                  "sakstype": "REGULERING",
                  "vedtaksdato": "2021-01-01",
                  "vedtakstype": "REGULERING",
                  "vedtaksresultat": "REGULERT",
                  "behandlendeEnhetKode": "4815",
                  "ytelseVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadVirkningstidspunkt": "2021-01-01",
                  "gjeldendeStonadStopptidspunkt": "2021-02-28",
                  "gjeldendeStonadUtbetalingsstart": "2021-01-01",
                  "gjeldendeStonadUtbetalingsstopp": "2021-02-28",
                  "månedsbeløp": [
                    {
                      "måned": "2021-01-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 20946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 0,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 0
                    },
                    {
                      "måned": "2021-02-01",
                      "stonadsklassifisering": "BOR_ALENE",
                      "bruttosats": 20946,
                      "nettosats": 20946,
                      "inntekter": [
                        {
                          "inntektstype": "ForventetInntekt",
                          "beløp": 0,
                          "tilhører": "BRUKER",
                          "erUtenlandsk": false
                        }
                      ],
                      "fradragSum": 0
                    }
                  ],
                  "versjon": 1609462923456,
                  "flyktningsstatus": "FLYKTNING"
                }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }
}
