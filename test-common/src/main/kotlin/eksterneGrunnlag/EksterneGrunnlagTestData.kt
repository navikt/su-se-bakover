package no.nav.su.se.bakover.test.eksterneGrunnlag

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.StøtterHentingAvEksternGrunnlag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlagMedFeilIÅrsgrunnlag

fun eksternGrunnlagHentet(): StøtterHentingAvEksternGrunnlag {
    return StøtterHentingAvEksternGrunnlag(
        skatt = EksterneGrunnlagSkatt.Hentet(
            søkers = nySkattegrunnlag(),
            eps = null,
        ),
    )
}

fun nyEksternGrunnlagHentetFeil(medEps: Fnr? = null): StøtterHentingAvEksternGrunnlag {
    return StøtterHentingAvEksternGrunnlag(
        skatt = EksterneGrunnlagSkatt.Hentet(
            søkers = nySkattegrunnlagMedFeilIÅrsgrunnlag(),
            eps = if (medEps != null) nySkattegrunnlagMedFeilIÅrsgrunnlag(fnr = medEps) else null,
        ),
    )
}
