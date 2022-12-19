package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import java.time.LocalDate

fun Sak.gjeldendeVedtaksdata(stønadsperiode: Stønadsperiode): GjeldendeVedtaksdata {
    return this.gjeldendeVedtaksdata(
        fraOgMed = stønadsperiode.periode.fraOgMed,
    )
}

// TODO jah+Jacob: Her har vi allerede en opprettet revurdering med gitt stønadsperiode. Kan vi gjøre noe her? Produksjonskoden bruker vedtakservice, men tenker den burde bruke samme domenekode som her.
fun Sak.gjeldendeVedtaksdata(fraOgMed: LocalDate): GjeldendeVedtaksdata {
    return this.kopierGjeldendeVedtaksdata(fraOgMed, fixedClock).getOrFail()
}
