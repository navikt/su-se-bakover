package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.domain.sak.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.nySakAlder
import no.nav.su.se.bakover.test.sakInfo
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

internal class FradragssjekkAvvikTest {
    @Test
    fun `erUtenforToleransegrense returnerer false nar det ikke er noen endring`() {
        erUtenforToleransegrense(
            gammeltMånedsbeløp = 1000,
            nyttMånedsbeløp = 1000,
        ) shouldBe false
    }

    @Test
    fun `erUtenforToleransegrense returnerer false ved akkurat 10 prosent endring`() {
        erUtenforToleransegrense(
            gammeltMånedsbeløp = 1000,
            nyttMånedsbeløp = 1100,
        ) shouldBe false
    }

    @Test
    fun `erUtenforToleransegrense returnerer true ved over 10 prosent endring begge veier`() {
        erUtenforToleransegrense(
            gammeltMånedsbeløp = 1000,
            nyttMånedsbeløp = 1101,
        ) shouldBe true

        erUtenforToleransegrense(
            gammeltMånedsbeløp = 1000,
            nyttMånedsbeløp = 899,
        ) shouldBe true
    }

    @Test
    fun `erUtenforToleransegrense handterer avrunding for belop som ikke gaar opp i hele prosenter`() {
        erUtenforToleransegrense(
            gammeltMånedsbeløp = 95,
            nyttMånedsbeløp = 104,
        ) shouldBe false

        erUtenforToleransegrense(
            gammeltMånedsbeløp = 95,
            nyttMånedsbeløp = 105,
        ) shouldBe true
    }

    @Test
    fun `erUtenforToleransegrense returnerer true nar gammelt månedsbelop er null og nytt er positivt`() {
        erUtenforToleransegrense(
            gammeltMånedsbeløp = 0,
            nyttMånedsbeløp = 1,
        ) shouldBe true
    }

    @Test
    fun `ulikt belop gir observasjon nar endringen i utbetalt belop er innenfor 10 prosent`() {
        val underTest = lagAlderssakMedFradrag(lokaltBeløp = 1000.0)
        val eksterntBeløp = underTest.lokaltBeløp + 500.0

        val avviksvurdering = finnAvvikForSak(
            sjekkgrunnlag = underTest.sjekkgrunnlag,
            måned = underTest.måned,
            oppslagsresultater = underTest.oppslagsresultater(eksterntBeløp),
            satsFactory = underTest.satsFactory,
            clock = fixedClock,
        )

        val observasjon = avviksvurdering.shouldBeType<Avviksvurdering.Diff>().avvik.single()
            .shouldBeType<Fradragsfunn.Observasjon>()

        observasjon.kode shouldBe Observasjonskode.INSIGNIFIKANT_BELOEPSDIFFERANSE
        observasjon.loggtekst shouldContain "innenfor toleransegrensen på 10%"
    }

    @Test
    fun `ulikt belop gir oppgave nar endringen i utbetalt belop er over 10 prosent nedover`() {
        val underTest = lagAlderssakMedFradrag(lokaltBeløp = 1000.0)
        val eksterntBeløp = underTest.lokaltBeløp + 2000.0

        val avviksvurdering = finnAvvikForSak(
            sjekkgrunnlag = underTest.sjekkgrunnlag,
            måned = underTest.måned,
            oppslagsresultater = underTest.oppslagsresultater(eksterntBeløp),
            satsFactory = underTest.satsFactory,
            clock = fixedClock,
        )

        val oppgaveavvik = avviksvurdering.shouldBeType<Avviksvurdering.Diff>().avvik.single()
            .shouldBeType<Fradragsfunn.Oppgaveavvik>()

        oppgaveavvik.kode shouldBe no.nav.su.se.bakover.domain.oppgave.OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10KR
        oppgaveavvik.oppgavetekst shouldContain "over toleransegrensen på 10%"
    }

    @Test
    fun `ulikt belop gir oppgave nar endringen i utbetalt belop er over 10 prosent oppover`() {
        val underTest = lagAlderssakMedFradrag(lokaltBeløp = 3000.0)
        val eksterntBeløp = 1000.0

        val avviksvurdering = finnAvvikForSak(
            sjekkgrunnlag = underTest.sjekkgrunnlag,
            måned = underTest.måned,
            oppslagsresultater = underTest.oppslagsresultater(eksterntBeløp),
            satsFactory = underTest.satsFactory,
            clock = fixedClock,
        )

        val oppgaveavvik = avviksvurdering.shouldBeType<Avviksvurdering.Diff>().avvik.single()
            .shouldBeType<Fradragsfunn.Oppgaveavvik>()

        withClue(oppgaveavvik.oppgavetekst) {
            oppgaveavvik.kode shouldBe no.nav.su.se.bakover.domain.oppgave.OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10KR
            oppgaveavvik.oppgavetekst shouldContain "over toleransegrensen på 10%"
        }
    }

    private fun lagAlderssakMedFradrag(
        lokaltBeløp: Double,
    ): UnderTest {
        val måned = april(2021)
        val sakInfo = sakInfo(type = Sakstype.ALDER)
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = fixedClock,
            stønadsperiode = Stønadsperiode.create(måned),
            sakOgSøknad = nySakAlder(
                clock = fixedClock,
                sakInfo = sakInfo,
            ),
            customGrunnlag = listOf(
                nyFradragsgrunnlag(
                    type = Fradragstype.Alderspensjon,
                    månedsbeløp = lokaltBeløp,
                    periode = måned,
                ),
            ),
        )

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = måned,
            vedtakListe = nonEmptyListOf(vedtak),
            clock = fixedClock,
        )

        val sjekkplan = lagSjekkplanForSak(
            sak = sakInfo,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
            måned = måned,
        )!!

        return UnderTest(
            sjekkgrunnlag = SjekkgrunnlagForSak(
                sjekkplan = sjekkplan,
                gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                gjeldendeMånedsutbetaling = sak.hentGjeldendeUtbetaling(måned.fraOgMed).getOrElse {
                    error("Forventet gjeldende utbetaling i test")
                }.beløp,
            ),
            satsFactory = satsFactoryTestPåDato(LocalDate.now(fixedClock)),
            lokaltBeløp = lokaltBeløp,
            måned = måned,
            sakInfo = sakInfo,
        )
    }

    private data class UnderTest(
        val sjekkgrunnlag: SjekkgrunnlagForSak,
        val satsFactory: satser.domain.SatsFactory,
        val lokaltBeløp: Double,
        val måned: Måned,
        val sakInfo: SakInfo,
    ) {
        fun oppslagsresultater(
            eksterntBeløp: Double,
        ): EksterneOppslagsresultater {
            return EksterneOppslagsresultater(
                aap = emptyMap(),
                pesysAlder = mapOf(sakInfo.fnr to EksterntOppslag.Funnet(eksterntBeløp)),
                pesysUføre = emptyMap(),
            )
        }
    }
}
