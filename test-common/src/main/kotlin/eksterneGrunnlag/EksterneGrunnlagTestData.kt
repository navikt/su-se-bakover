package no.nav.su.se.bakover.test.eksterneGrunnlag

import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.StøtterHentingAvEksternGrunnlag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag

fun eksternGrunnlagHentet(): StøtterHentingAvEksternGrunnlag {
    return StøtterHentingAvEksternGrunnlag(
        skatt = EksterneGrunnlagSkatt.Hentet(
            søkers = nySkattegrunnlag(),
            eps = null,
        ),
    )
}
