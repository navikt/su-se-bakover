package no.nav.su.se.bakover.statistikk.stønad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.skyscreamer.jsonassert.JSONAssert
import statistikk.domain.StønadstatistikkDto
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.uføre.domain.Uføregrad
import java.time.LocalDate
import java.time.YearMonth

/*
Merk ingen av disse går igjennom den faktiske mappingen av data og har ingen typesikkerhet what so ever.
Kun sikkerthet mtp at gitte datafelt blir publisert på kafka. Kan vurderes å slettes hvis disse flyttes inn i et
eget service eller databaselag for testing.
 */
internal class StønadsstatistikkTest {

    @Test
    fun `Stans gir nullutbetaling`() {
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()
        assert(
            event = StatistikkEvent.Stønadsvedtak(vedtak) { sak },
            vedtakstype = "STANS",
            vedtaksresultat = "STANSET",
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
            funksjonellTid = "2021-01-01T01:03:20.456789Z",
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
            funksjonellTid = "2021-01-01T01:03:02.456789Z",
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
            funksjonellTid = "2021-01-01T01:02:42.456789Z",
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
        månedsbeløp: String = "[]",
        opphørsgrunn: String? = null,
        opphørsdato: LocalDate? = null,
        ytelseVirkningstidspunkt: LocalDate = event.vedtak.periode.fraOgMed,
        funksjonellTid: String = "2021-01-01T01:02:03.456789Z",
    ) {
        val kafkaPublisherMock: KafkaPublisher = mock()

        StatistikkEventObserverBuilder(
            kafkaPublisher = kafkaPublisherMock,
            personService = mock {},
            clock = fixedClock,
            gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
            mock(),
        ).statistikkService.handle(event)
        val sak = event.hentSak()
        val stonadstype = when (sak.type) {
            Sakstype.ALDER -> StønadstatistikkDto.Stønadstype.SU_ALDER
            Sakstype.UFØRE -> StønadstatistikkDto.Stønadstype.SU_UFØR
        }
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe "supstonad.aapen-su-stonad-statistikk-v1" },
            // language=JSON
            argThat {
                // language=JSON
                JSONAssert.assertEquals(
                    """
                {
                  "harUtenlandsOpphold": "NEI",
                  "statistikkAarMaaned": "${YearMonth.now()}",
                  "funksjonellTid": "$funksjonellTid",
                  "tekniskTid": "2021-01-01T01:02:03.456789Z",
                  "stonadstype": "$stonadstype",
                  "sakId": "${sak.id}",
                  "personnummer": "${sak.fnr}",
                  "vedtaksdato": "${event.vedtak.opprettet.toLocalDate(zoneIdOslo)}",
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
