package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

class ÅrsakTilManuellReguleringJsonTest {

    private val årsakUtbetalingFeilet = ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet("WOLOLO")
    private val årsakForventetInntektErStørreEnn0 =
        ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0("Watch your clever mouth, B-!")
    private val årsakMidlertidigStanset = ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset("Hell, it's about time")
    private val årsakDelvisOpphør =
        ÅrsakTilManuellRegulering.DelvisOpphør(Perioder.create(listOf(mai(2021), juli(2021))), "Zug Zug")
    private val årsakVedtakslinjeIkkeSammenhengende =
        ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende("Me not that kind of orc")

    private val årsakFradragUtenlandsinntekt =
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = """
                try finger
                but hole
            """.trimIndent(),
        )
    private val årsakFinnesFlerePerioder =
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Could this be dog?",
        )
    private val årsakBeløpErStørreEnForventet =
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BeløpErStørreEnForventet(
            fradragskategori = Fradragstype.Kategori.Uføretrygd,
            fradragTilhører = FradragTilhører.EPS,
            begrunnelse = """When in doubt, dont think - simply shout "For Democracy!" and charge head-first into your problems.""",
            eksterntBeløpEtterRegulering = BigDecimal.ONE,
            forventetBeløpEtterRegulering = BigDecimal.TEN,
        )
    private val årsakBrukerManglerSupplement =
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = """It's dangerous to go alone, take this!""",
        )

    private val årsakSupplementInneholderIkkeFradrag =
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Hey, you. You're finally awake.",
        )
    private val supplementFlerePerioder =
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Do you think i have big mom energy?",
            eksterneReguleringsvedtakperioder = listOf(
                PeriodeMedOptionalTilOgMed(1.mai(2021)),
                PeriodeMedOptionalTilOgMed(1.juli(2021)),
            ),
        )

    private val årsakMismatch =
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MismatchMellomBeløpFraSupplementOgFradrag(
            fradragskategori = Fradragstype.Kategori.Alderspensjon,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "Boy",
            eksterntBeløpFørRegulering = BigDecimal.ONE,
            vårtBeløpFørRegulering = BigDecimal.TWO,
        )

    @Test
    fun `mapper domene-type til json-type`() {
        ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.toDbJson() shouldBe """{"type":"FradragMåHåndteresManuelt"}"""
        ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet.toDbJson() shouldBe """{"type":"UtbetalingFeilet"}"""
        ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset.toDbJson() shouldBe """{"type":"YtelseErMidlertidigStanset","begrunnelse":null}"""
        ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0.toDbJson() shouldBe """{"type":"ForventetInntektErStørreEnn0","begrunnelse":null}"""

        årsakUtbetalingFeilet.toDbJson() shouldBe """{"type":"AutomatiskSendingTilUtbetalingFeilet","begrunnelse":"WOLOLO"}"""
        årsakForventetInntektErStørreEnn0.toDbJson() shouldBe """{"type":"ForventetInntektErStørreEnn0","begrunnelse":"Watch your clever mouth, B-!"}"""
        årsakMidlertidigStanset.toDbJson() shouldBe """{"type":"YtelseErMidlertidigStanset","begrunnelse":"Hell, it's about time"}"""
        årsakDelvisOpphør.toDbJson() shouldBe """{"type":"DelvisOpphør","opphørsperioder":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}],"begrunnelse":"Zug Zug"}"""
        årsakVedtakslinjeIkkeSammenhengende.toDbJson() shouldBe """{"type":"VedtakstidslinjeErIkkeSammenhengende","begrunnelse":"Me not that kind of orc"}"""

        årsakFradragUtenlandsinntekt.toDbJson() shouldBe """{"type":"FradragErUtenlandsinntekt","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"try finger\nbut hole"}"""
        årsakFinnesFlerePerioder.toDbJson() shouldBe """{"type":"FinnesFlerePerioderAvFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Could this be dog?"}"""
        årsakBeløpErStørreEnForventet.toDbJson() shouldBe """{"type":"BeløpErStørreEnForventet","fradragskategori":"Uføretrygd","fradragTilhører":"EPS","begrunnelse":"When in doubt, dont think - simply shout \"For Democracy!\" and charge head-first into your problems.","eksterntBeløpEtterRegulering":"1","forventetBeløpEtterRegulering":"10"}"""
        årsakBrukerManglerSupplement.toDbJson() shouldBe """{"type":"BrukerManglerSupplement","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"It's dangerous to go alone, take this!"}"""
        årsakSupplementInneholderIkkeFradrag.toDbJson() shouldBe """{"type":"SupplementInneholderIkkeFradraget","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Hey, you. You're finally awake."}"""
        supplementFlerePerioder.toDbJson() shouldBe """{"type":"SupplementHarFlereVedtaksperioderForFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Do you think i have big mom energy?","eksterneReguleringsvedtakperioder":[{"fraOgMed":"2021-05-01","tilOgMed":null},{"fraOgMed":"2021-07-01","tilOgMed":null}]}"""
        årsakMismatch.toDbJson() shouldBe """{"type":"MismatchMellomBeløpFraSupplementOgFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Boy","eksterntBeløpFørRegulering":"1","vårtBeløpFørRegulering":"2"}"""
    }

    @Test
    fun `mapper json-type til domene-type`() {
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"FradragMåHåndteresManuelt"}""").toDomain() shouldBe ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"UtbetalingFeilet"}""").toDomain() shouldBe ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"YtelseErMidlertidigStanset","begrunnelse":null}""").toDomain() shouldBe ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"ForventetInntektErStørreEnn0","begrunnelse":null}""").toDomain() shouldBe ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0

        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"AutomatiskSendingTilUtbetalingFeilet","begrunnelse":"WOLOLO"}""").toDomain() shouldBe årsakUtbetalingFeilet
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"ForventetInntektErStørreEnn0","begrunnelse":"Watch your clever mouth, B-!"}""").toDomain() shouldBe årsakForventetInntektErStørreEnn0
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"YtelseErMidlertidigStanset","begrunnelse":"Hell, it's about time"}""").toDomain() shouldBe årsakMidlertidigStanset
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"DelvisOpphør","opphørsperioder":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}],"begrunnelse":"Zug Zug"}""").toDomain() shouldBe årsakDelvisOpphør
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"VedtakstidslinjeErIkkeSammenhengende","begrunnelse":"Me not that kind of orc"}""").toDomain() shouldBe årsakVedtakslinjeIkkeSammenhengende

        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"FradragErUtenlandsinntekt","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"try finger\nbut hole"}""").toDomain() shouldBe årsakFradragUtenlandsinntekt
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"FinnesFlerePerioderAvFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Could this be dog?"}""").toDomain() shouldBe årsakFinnesFlerePerioder
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"BeløpErStørreEnForventet","fradragskategori":"Uføretrygd","fradragTilhører":"EPS","begrunnelse":"When in doubt, dont think - simply shout \"For Democracy!\" and charge head-first into your problems.","eksterntBeløpEtterRegulering":"1","forventetBeløpEtterRegulering":"10"}""").toDomain() shouldBe årsakBeløpErStørreEnForventet
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"BrukerManglerSupplement","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"It's dangerous to go alone, take this!"}""").toDomain() shouldBe årsakBrukerManglerSupplement
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"SupplementInneholderIkkeFradraget","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Hey, you. You're finally awake."}""").toDomain() shouldBe årsakSupplementInneholderIkkeFradrag
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"SupplementHarFlereVedtaksperioderForFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Do you think i have big mom energy?","eksterneReguleringsvedtakperioder":[{"fraOgMed":"2021-05-01","tilOgMed":null},{"fraOgMed":"2021-07-01","tilOgMed":null}]}""").toDomain() shouldBe supplementFlerePerioder
        deserialize<ÅrsakTilManuellReguleringJson>("""{"type":"MismatchMellomBeløpFraSupplementOgFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Boy","eksterntBeløpFørRegulering":"1","vårtBeløpFørRegulering":"2"}""").toDomain() shouldBe årsakMismatch
    }

    @Test
    fun `mapper set med domene-type til liste av json-type`() {
        setOf(årsakMismatch).toDbJson() shouldBe """[{"type":"MismatchMellomBeløpFraSupplementOgFradrag","fradragskategori":"Alderspensjon","fradragTilhører":"BRUKER","begrunnelse":"Boy","eksterntBeløpFørRegulering":"1","vårtBeløpFørRegulering":"2"}]"""
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
    }

    @Test
    fun `mapper reguleringstype til årsak`() {
        Reguleringstype.AUTOMATISK.årsakerTilManuellReguleringJson() shouldBe "[]"
        Reguleringstype.MANUELL(setOf(årsakDelvisOpphør))
            .årsakerTilManuellReguleringJson() shouldBe """[{"type":"DelvisOpphør","opphørsperioder":[{"fraOgMed":"2021-05-01","tilOgMed":"2021-05-31"},{"fraOgMed":"2021-07-01","tilOgMed":"2021-07-31"}],"begrunnelse":"Zug Zug"}]"""
    }
}
