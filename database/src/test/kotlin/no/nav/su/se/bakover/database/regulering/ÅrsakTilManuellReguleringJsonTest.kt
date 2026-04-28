package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

class ÅrsakTilManuellReguleringJsonTest {

    private val årsakUtbetalingFeilet = ÅrsakTilManuellRegulering.Historisk.AutomatiskSendingTilUtbetalingFeilet("WOLOLO")
    private val årsakForventetInntektErStørreEnn0 =
        ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0()
    private val årsakMidlertidigStanset = ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset("Hell, it's about time")
    private val årsakDelvisOpphør =
        ÅrsakTilManuellRegulering.Historisk.DelvisOpphør(Perioder.create(listOf(mai(2021), juli(2021))), "Zug Zug")
    private val årsakVedtakslinjeIkkeSammenhengende =
        ÅrsakTilManuellRegulering.Historisk.VedtakstidslinjeErIkkeSammenhengende("Me not that kind of orc")

    private val årsakFradragUtenlandsinntekt =
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = """
                try finger
                but hole
            """.trimIndent(),
        )
    private val årsakFinnesFlerePerioder =
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Could this be dog?",
        )
    private val årsakDifferanseEtterRegulering =
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
            fradragskategori = Fradragstype.Kategori.Uføretrygd,
            fradragTilhører = FradragTilhører.EPS,
            begrunnelse = """When in doubt, dont think - simply shout "For Democracy!" and charge head-first into your problems.""",
            eksternNettoBeløpEtterRegulering = BigDecimal.ONE,
            forventetBeløpEtterRegulering = BigDecimal.TEN,
            eksternBruttoBeløpEtterRegulering = BigDecimal.ONE,
            vårtBeløpFørRegulering = BigDecimal.ONE,
        )
    private val årsakBrukerManglerSupplement =
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.BrukerManglerSupplement(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = """It's dangerous to go alone, take this!""",
        )

    private val årsakSupplementInneholderIkkeFradrag =
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Hey, you. You're finally awake.",
        )
    private val supplementFlerePerioder =
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Do you think i have big mom energy?",
            eksterneReguleringsvedtakperioder = listOf(
                PeriodeMedOptionalTilOgMed(1.mai(2021)),
                PeriodeMedOptionalTilOgMed(1.juli(2021)),
            ),
        )

    private val årsakMismatch =
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.DifferanseFørRegulering(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Boy",
            vårtBeløpFørRegulering = BigDecimal.TWO,
            eksternNettoBeløpFørRegulering = BigDecimal.ONE,
            eksternBruttoBeløpFørRegulering = BigDecimal.ONE,
        )

    @Test
    fun `mapper domene-type til json-type`() {
        årsakMidlertidigStanset.toDbJson() shouldBe """{"type":"YtelseErMidlertidigStanset","begrunnelse":"Hell, it's about time"}"""
    }

    @Test
    fun `mapper json-type til domene-type`() {
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"UtbetalingFeilet"}""").toDomain() shouldBe ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"YtelseErMidlertidigStanset","begrunnelse":null}""").toDomain() shouldBe ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"ForventetInntektErStørreEnn0","begrunnelse":null}""").toDomain() shouldBe årsakForventetInntektErStørreEnn0

        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"AutomatiskSendingTilUtbetalingFeilet","begrunnelse":"WOLOLO"}""").toDomain() shouldBe årsakUtbetalingFeilet
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"YtelseErMidlertidigStanset","begrunnelse":"Hell, it's about time"}""").toDomain() shouldBe årsakMidlertidigStanset
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"DelvisOpphør","opphørsperioder":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}],"begrunnelse":"Zug Zug"}""").toDomain() shouldBe årsakDelvisOpphør
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"VedtakstidslinjeErIkkeSammenhengende","begrunnelse":"Me not that kind of orc"}""").toDomain() shouldBe årsakVedtakslinjeIkkeSammenhengende

        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"FradragErUtenlandsinntekt","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"try finger\nbut hole"}""").toDomain() shouldBe årsakFradragUtenlandsinntekt
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"FinnesFlerePerioderAvFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Could this be dog?"}""").toDomain() shouldBe årsakFinnesFlerePerioder
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"DifferanseEtterRegulering","fradragskategori":"Uføretrygd","fradragTilhører":"EPS","begrunnelse":"When in doubt, dont think - simply shout \"For Democracy!\" and charge head-first into your problems.","vårtBeløpFørRegulering": "1","eksternNettoBeløpEtterRegulering":"1","eksternBruttoBeløpEtterRegulering":"1","forventetBeløpEtterRegulering":"10"}""").toDomain() shouldBe årsakDifferanseEtterRegulering
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"BrukerManglerSupplement","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"It's dangerous to go alone, take this!"}""").toDomain() shouldBe årsakBrukerManglerSupplement
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"SupplementInneholderIkkeFradraget","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Hey, you. You're finally awake."}""").toDomain() shouldBe årsakSupplementInneholderIkkeFradrag
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"SupplementHarFlereVedtaksperioderForFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Do you think i have big mom energy?","eksterneReguleringsvedtakperioder":[{"fraOgMed":"2021-05-01","tilOgMed":null},{"fraOgMed":"2021-07-01","tilOgMed":null}]}""").toDomain() shouldBe supplementFlerePerioder
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"DifferanseFørRegulering","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Boy","eksternNettoBeløpFørRegulering":"1","eksternBruttoBeløpFørRegulering":"1","vårtBeløpFørRegulering":"2"}""").toDomain() shouldBe årsakMismatch
    }

    @Test
    fun `mapper liste med json-type til domene-type`() {
        //language=json
        val input = """[
            {"type":"FradragErUtenlandsinntekt","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"try finger\nbut hole"},
            {"type":"VedtakstidslinjeErIkkeSammenhengende","begrunnelse":"Me not that kind of orc"}
        ]
        """.trimIndent()

        ÅrsakTilManuellReguleringJson.toDomain(input) shouldBe setOf(
            årsakFradragUtenlandsinntekt,
            årsakVedtakslinjeIkkeSammenhengende,
        )

        ÅrsakTilManuellReguleringJson.toDomain("[]") shouldBe emptySet()

        assertThrows<NullPointerException> {
            ÅrsakTilManuellReguleringJson.toDomain("null")
        }
    }
}
