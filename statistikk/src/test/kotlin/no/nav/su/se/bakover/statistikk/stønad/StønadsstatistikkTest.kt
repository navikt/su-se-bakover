package no.nav.su.se.bakover.statistikk.stønad

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vedtakIverksattAutomatiskRegulering
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class StønadsstatistikkTest {

    @Test
    fun `Stans gir nullutbetaling`() {
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(periode = år(2021))
        assert(
            sak = sak,
            event = StatistikkEvent.Stønadsvedtak(vedtak),
            vedtakstype = "STANS",
            vedtaksresultat = "STANSET",
            sakstype = "STANS",
            funksjonellTid = "2021-01-01T01:02:40.456789Z",
        )
    }

    @Test
    fun `Gjenopptak sender med riktig månedsbeløp`() {
        val periode = januar(2021)
        val (sak, vedtak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            periode = periode,
            sakOgVedtakSomKanRevurderes = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                periode = periode,
                sakOgVedtakSomKanRevurderes = iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(periode),
                    customVilkår = listOf(
                        innvilgetUførevilkår(
                            forventetInntekt = 36000,
                            uføregrad = Uføregrad.parse(50),
                            periode = periode,
                        ),
                    ),
                    clock = tikkendeFixedClock,
                ).let { Pair(it.first, it.third as VedtakSomKanRevurderes) },
                clock = tikkendeFixedClock,
            ),
            clock = tikkendeFixedClock,
        )
        assert(
            sak = sak,
            event = StatistikkEvent.Stønadsvedtak(vedtak),
            vedtakstype = "GJENOPPTAK",
            vedtaksresultat = "GJENOPPTATT",
            sakstype = "GJENOPPTAK",
            // language=JSON
            månedsbeløp = """[
                {
                  "måned": "2021-01-01",
                  "stonadsklassifisering": "BOR_ALENE",
                  "bruttosats": 20946,
                  "nettosats": 17946,
                  "fradragSum": 3000,
                  "inntekter": [
                    {
                      "inntektstype": "ForventetInntekt",
                      "beløp": 3000,
                      "tilhører": "BRUKER",
                      "erUtenlandsk": false
                    }
                  ]
                }
            ]
            """.trimIndent(),
            funksjonellTid = vedtak.opprettet.toString(),
        )
    }

    @Test
    fun `Gjenopptak bruker nyeste beregning for hver måned`() {
        val stønadsperiode = Periode.create(1.januar(2021), 28.februar(2021))
        var sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(stønadsperiode),
            clock = fixedClock,
        )
        lateinit var gjenopptakVedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse
        // revurderer 0 -> 7500 i arbeidsinntekt i februar
        sakOgVedtak = vedtakRevurderingIverksattInnvilget(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            revurderingsperiode = Periode.create(1.februar(2021), 28.februar(2021)),
            clock = fixedClock.plus(1, ChronoUnit.SECONDS),
        )
        sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stønadsperiode,
            clock = fixedClock.plus(2, ChronoUnit.SECONDS),
        )
        val (sak, _) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stønadsperiode,
            clock = fixedClock.plus(3, ChronoUnit.SECONDS),
        ).also {
            sakOgVedtak = it
            gjenopptakVedtak = it.second
        }
        assert(
            sak = sak,
            event = StatistikkEvent.Stønadsvedtak(gjenopptakVedtak),
            vedtakstype = "GJENOPPTAK",
            vedtaksresultat = "GJENOPPTATT",
            sakstype = "GJENOPPTAK",
            // language=JSON
            månedsbeløp = """[
                {
                  "måned": "2021-01-01",
                  "stonadsklassifisering": "BOR_ALENE",
                  "bruttosats": 20946,
                  "nettosats": 20946,
                  "fradragSum": 0,
                  "inntekter": [
                    {
                      "inntektstype": "ForventetInntekt",
                      "beløp": 0,
                      "tilhører": "BRUKER",
                      "erUtenlandsk": false
                    }
                  ]
                },
                {
                  "måned": "2021-02-01",
                  "stonadsklassifisering": "BOR_ALENE",
                  "bruttosats": 20946,
                  "nettosats": 13446,
                  "fradragSum": 7500,
                  "inntekter": [
                    {
                      "inntektstype": "Arbeidsinntekt",
                      "beløp": 7500,
                      "tilhører": "BRUKER",
                      "erUtenlandsk": false
                    }
                  ]
                }
            ]
            """.trimIndent(),
            funksjonellTid = "2021-01-01T01:02:06.456789Z",
        )
    }

    @Test
    fun `Gjenopptak for en kortere periode enn søknadsperioden bruker nyeste beregning for hver måned`() {
        val stønadsperiode = Periode.create(1.januar(2021), 28.februar(2021))
        var sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(stønadsperiode),
            clock = fixedClock,
        )
        val stansOgGjenopptagelsesperiode = Periode.create(1.februar(2021), 28.februar(2021))
        lateinit var gjenopptakVedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse
        sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stansOgGjenopptagelsesperiode,
            clock = fixedClock.plus(2, ChronoUnit.SECONDS),
        )
        vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stansOgGjenopptagelsesperiode,
            clock = fixedClock.plus(3, ChronoUnit.SECONDS),
        ).also {
            sakOgVedtak = it
            gjenopptakVedtak = it.second
        }
        assert(
            sak = sakOgVedtak.first,
            event = StatistikkEvent.Stønadsvedtak(gjenopptakVedtak),
            vedtakstype = "GJENOPPTAK",
            vedtaksresultat = "GJENOPPTATT",
            sakstype = "GJENOPPTAK",
            // language=JSON
            månedsbeløp = """[
                {
                  "måned": "2021-02-01",
                  "stonadsklassifisering": "BOR_ALENE",
                  "bruttosats": 20946,
                  "nettosats": 20946,
                  "fradragSum": 0,
                  "inntekter": [
                    {
                      "inntektstype": "ForventetInntekt",
                      "beløp": 0,
                      "tilhører": "BRUKER",
                      "erUtenlandsk": false
                    }
                  ]
                }
            ]
            """.trimIndent(),
            ytelseVirkningstidspunkt = januar(2021).fraOgMed,
            funksjonellTid = "2021-01-01T01:02:06.456789Z",
        )
    }

    @Test
    fun `stønadsstatistikk for regulering`() {
        val (sak, regulering) = vedtakIverksattAutomatiskRegulering(
            stønadsperiode = Stønadsperiode.create(
                periode = Periode.create(1.januar(2021), 28.februar(2021)),
            ),
        )
        assert(
            sak = sak,
            event = StatistikkEvent.Stønadsvedtak(regulering),
            vedtakstype = "REGULERING",
            vedtaksresultat = "REGULERT",
            sakstype = "REGULERING",
            // language=JSON
            månedsbeløp = """[
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
            ]
            """.trimIndent(),
            ytelseVirkningstidspunkt = januar(2021).fraOgMed,
        )
    }

    @Test
    fun `stønadsstatistikk for innvilget revurdering`() {
        val (sak, regulering) = vedtakRevurderingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                periode = januar(2021),
            ),
            revurderingsperiode = januar(2021),
        )
        assert(
            sak = sak,
            event = StatistikkEvent.Stønadsvedtak(regulering),
            vedtakstype = "REVURDERING",
            vedtaksresultat = "INNVILGET",
            sakstype = "REVURDERING",
            // language=JSON
            månedsbeløp = """[
                {
                  "måned": "2021-01-01",
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
            ]
            """.trimIndent(),
            ytelseVirkningstidspunkt = januar(2021).fraOgMed,
            funksjonellTid = regulering.opprettet.toString(),
        )
    }

    @Test
    fun `stønadsstatistikk for opphørt revurdering`() {
        val (sak, revurdering) = vedtakRevurdering(
            stønadsperiode = Stønadsperiode.create(
                periode = januar(2021),
            ),
            revurderingsperiode = januar(2021),
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag(periode = januar(2021))),
        ).let { (sak, vedtak) -> sak to vedtak as VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering }
        assert(
            sak = sak,
            event = StatistikkEvent.Stønadsvedtak(revurdering),
            vedtakstype = "REVURDERING",
            vedtaksresultat = "OPPHØRT",
            sakstype = "REVURDERING",
            ytelseVirkningstidspunkt = januar(2021).fraOgMed,
            opphørsgrunn = "UFØRHET",
            opphørsdato = januar(2021).fraOgMed,
            funksjonellTid = revurdering.opprettet.toString(),
        )
    }

    @Test
    fun `stønadsstatistikk for innvilget søknadsbehandling`() {
        val (sak, søknadsbehandling) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                periode = januar(2021),
            ),
        )
        assert(
            sak = sak,
            event = StatistikkEvent.Stønadsvedtak(søknadsbehandling),
            vedtakstype = "SØKNAD",
            vedtaksresultat = "INNVILGET",
            sakstype = "SØKNAD",
            // language=JSON
            månedsbeløp = """[
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
                }
            ]
            """.trimIndent(),
            ytelseVirkningstidspunkt = januar(2021).fraOgMed,
            funksjonellTid = søknadsbehandling.opprettet.toString(),
        )
    }

    private fun assert(
        sak: Sak,
        event: StatistikkEvent.Stønadsvedtak,
        vedtakstype: String,
        vedtaksresultat: String,
        sakstype: String,
        månedsbeløp: String = "[]",
        opphørsgrunn: String? = null,
        opphørsdato: LocalDate? = null,
        ytelseVirkningstidspunkt: LocalDate = event.vedtak.periode.fraOgMed,
        funksjonellTid: String = "2021-01-01T01:02:03.456789Z",
    ) {
        val kafkaPublisherMock: KafkaPublisher = mock()

        StatistikkEventObserverBuilder(
            kafkaPublisher = kafkaPublisherMock,
            personService = mock {
                on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("55").right()
            },
            sakRepo = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            clock = fixedClock,
            gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
        ).statistikkService.handle(event)

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe "supstonad.aapen-su-stonad-statistikk-v1" },
            // language=JSON
            argThat {
                // language=JSON
                JSONAssert.assertEquals(
                    """
                {
                  "funksjonellTid": "$funksjonellTid",
                  "tekniskTid": "2021-01-01T01:02:03.456789Z",
                  "stonadstype": "SU_UFØR",
                  "sakId": "${event.vedtak.sakinfo().sakId}",
                  "aktorId": 55,
                  "sakstype": "$sakstype",
                  "vedtaksdato": "2021-01-01",
                  "vedtakstype": "$vedtakstype",
                  "vedtaksresultat": "$vedtaksresultat",
                  "behandlendeEnhetKode": "4815",
                  "ytelseVirkningstidspunkt": "$ytelseVirkningstidspunkt",
                  "gjeldendeStonadVirkningstidspunkt": "${event.vedtak.periode.fraOgMed}",
                  "gjeldendeStonadStopptidspunkt": "${event.vedtak.periode.tilOgMed}",
                  "gjeldendeStonadUtbetalingsstart": "${event.vedtak.periode.fraOgMed}",
                  "gjeldendeStonadUtbetalingsstopp": "${event.vedtak.periode.tilOgMed}",
                  "månedsbeløp": $månedsbeløp,
                  ${if (opphørsgrunn != null) """"opphorsgrunn":"$opphørsgrunn",""" else ""}
                  ${if (opphørsdato != null) """"opphorsdato":"$opphørsdato",""" else ""}
                  "versjon": "87a3a5155bf00b4d6854efcc24e8b929549c9302",
                  "flyktningsstatus": "FLYKTNING"
                }
                    """.trimIndent(),
                    it,
                    true,
                )
            },
        )
    }
}
