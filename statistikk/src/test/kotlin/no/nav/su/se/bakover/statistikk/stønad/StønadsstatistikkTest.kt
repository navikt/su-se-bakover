package no.nav.su.se.bakover.statistikk.stønad

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
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

internal class StønadsstatistikkTest {

    @Test
    fun `Stans gir nullutbetaling`() {
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()
        assert(
            event = StatistikkEvent.Stønadsvedtak(vedtak) { sak },
            vedtakstype = "STANS",
            vedtaksresultat = "STANSET",
            sakstype = "STANS",
            funksjonellTid = "${vedtak.opprettet}",
        )
    }

    @Test
    fun `Gjenopptak sender med riktig månedsbeløp`() {
        val periode = januar(2021)
        val clock = tikkendeFixedClock()
        val (sak, vedtak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            periode = periode,
            sakOgVedtakSomKanRevurderes = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                periode = periode,
                sakOgVedtakSomKanRevurderes = iverksattSøknadsbehandlingUføre(
                    clock = clock,
                    stønadsperiode = Stønadsperiode.create(periode),
                    customVilkår = listOf(
                        innvilgetUførevilkår(
                            forventetInntekt = 36000,
                            uføregrad = Uføregrad.parse(50),
                            periode = periode,
                        ),
                    ),
                ).let { Pair(it.first, it.third as VedtakSomKanRevurderes) },
                clock = clock,
            ).let { it.first to it.second },
            clock = clock,
        )
        assert(
            event = StatistikkEvent.Stønadsvedtak(vedtak) { sak },
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
        val clock = TikkendeKlokke()
        val stønadsperiode = Periode.create(1.januar(2021), 28.februar(2021))
        var sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(stønadsperiode),
            clock = clock,
        )
        lateinit var gjenopptakVedtak: VedtakGjenopptakAvYtelse
        // revurderer 0 -> 7500 i arbeidsinntekt i februar
        sakOgVedtak = vedtakRevurderingIverksattInnvilget(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            revurderingsperiode = Periode.create(1.februar(2021), 28.februar(2021)),
            clock = clock,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.februar(2021), 28.februar(2021)),
                    arbeidsinntekt = 7500.0,
                ),
            ),
        )
        sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stønadsperiode,
            clock = clock,
        ).let { it.first to it.second }
        val (sak, _) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stønadsperiode,
            clock = clock,
        ).also {
            sakOgVedtak = it
            gjenopptakVedtak = it.second
        }
        assert(
            event = StatistikkEvent.Stønadsvedtak(gjenopptakVedtak) { sak },
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
            funksjonellTid = "2021-01-01T01:03:45.456789Z",
        )
    }

    @Test
    fun `Gjenopptak for en kortere periode enn søknadsperioden bruker nyeste beregning for hver måned`() {
        val stønadsperiode = Periode.create(1.januar(2021), 28.februar(2021))
        val clock = TikkendeKlokke()
        var sakOgVedtak: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(stønadsperiode),
            clock = clock,
        )
        val stansOgGjenopptagelsesperiode = Periode.create(1.februar(2021), 28.februar(2021))
        lateinit var gjenopptakVedtak: VedtakGjenopptakAvYtelse
        sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stansOgGjenopptagelsesperiode,
            clock = clock,
        ).let { it.first to it.second }
        vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            periode = stansOgGjenopptagelsesperiode,
            clock = clock,
        ).also {
            sakOgVedtak = it
            gjenopptakVedtak = it.second
        }
        assert(
            event = StatistikkEvent.Stønadsvedtak(gjenopptakVedtak) { sakOgVedtak.first },
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
            funksjonellTid = "2021-01-01T01:03:16.456789Z",
        )
    }

    @Test
    fun `stønadsstatistikk for regulering`() {
        val (sak, regulering) = vedtakIverksattAutomatiskRegulering(
            stønadsperiode = Stønadsperiode.create(
                periode = Periode.create(1.januar(2021), 28.februar(2021)),
            ),
            clock = TikkendeKlokke(),
        )
        assert(
            event = StatistikkEvent.Stønadsvedtak(regulering) { sak },
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
            funksjonellTid = "2021-01-01T01:02:48.456789Z",
        )
    }

    @Test
    fun `stønadsstatistikk for innvilget revurdering`() {
        val (sak, regulering) = vedtakRevurderingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                periode = januar(2021),
            ),
            revurderingsperiode = januar(2021),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 7500.0,
                ),
            ),
        )
        assert(
            event = StatistikkEvent.Stønadsvedtak(regulering) { sak },
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
        ).let { (sak, vedtak) -> sak to vedtak as VedtakOpphørMedUtbetaling }
        assert(
            event = StatistikkEvent.Stønadsvedtak(revurdering) { sak },
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
            event = StatistikkEvent.Stønadsvedtak(søknadsbehandling) { sak },
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
