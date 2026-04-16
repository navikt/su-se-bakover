package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.nySakAlder
import no.nav.su.se.bakover.test.sakInfo
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt

internal class FradragssjekkPlanTest {

    @Test
    fun `alderssjekkpunkt ignorerer utenlandsk alderspensjon`() {
        val måned = april(2026)
        val sak = sakInfo(type = Sakstype.ALDER)
        val gjeldendeVedtaksdata = lagGjeldendeVedtaksdataForAlderssak(
            sak = sak,
            måned = måned,
            fradragsgrunnlag = listOf(
                nyFradragsgrunnlag(
                    type = Fradragstype.Alderspensjon,
                    månedsbeløp = 2500.0,
                    periode = måned,
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 2500,
                        valuta = "SEK",
                        kurs = 1.0,
                    ),
                ),
            ),
        )

        val sjekkplan = lagSjekkplanForSak(
            sak = sak,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
            måned = måned,
        )!!

        sjekkplan.sjekkpunkter.single().lokaltBeløp shouldBe null
    }

    @Test
    fun `alderssjekkpunkt bruker kun ikke-utenlandsk alderspensjon nar begge finnes`() {
        val måned = april(2026)
        val sak = sakInfo(type = Sakstype.ALDER)
        val gjeldendeVedtaksdata = lagGjeldendeVedtaksdataForAlderssak(
            sak = sak,
            måned = måned,
            fradragsgrunnlag = listOf(
                nyFradragsgrunnlag(
                    type = Fradragstype.Alderspensjon,
                    månedsbeløp = 2500.0,
                    periode = måned,
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 2500,
                        valuta = "SEK",
                        kurs = 1.0,
                    ),
                ),
                nyFradragsgrunnlag(
                    type = Fradragstype.Alderspensjon,
                    månedsbeløp = 1500.0,
                    periode = måned,
                ),
            ),
        )

        val sjekkplan = lagSjekkplanForSak(
            sak = sak,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
            måned = måned,
        )!!

        sjekkplan.sjekkpunkter.single().lokaltBeløp shouldBe 1500.0
    }

    private fun lagGjeldendeVedtaksdataForAlderssak(
        sak: SakInfo,
        måned: Måned,
        fradragsgrunnlag: List<Fradragsgrunnlag>,
    ): GjeldendeVedtaksdata {
        val (_, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = fixedClock,
            stønadsperiode = Stønadsperiode.create(måned),
            sakOgSøknad = nySakAlder(
                clock = fixedClock,
                sakInfo = sak,
            ),
            customGrunnlag = fradragsgrunnlag,
        )

        return GjeldendeVedtaksdata(
            periode = måned,
            vedtakListe = nonEmptyListOf(vedtak),
            clock = fixedClock,
        )
    }
}
