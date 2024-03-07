package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import java.time.Clock

fun Sak.hentSisteInnvilgetSøknadsbehandlingGrunnlagFiltrerVekkSøknadsbehandling(
    søknadsbehandlingId: SøknadsbehandlingId,
    clock: Clock,
): Either<FeilVedHentingAvGjeldendeVedtaksdataForPeriode, Pair<Periode, GrunnlagsdataOgVilkårsvurderinger>> {
    return this.vedtakListe.filterIsInstance<VedtakInnvilgetSøknadsbehandling>().filter {
        it.behandling.id != søknadsbehandlingId
    }.maxByOrNull { it.opprettet.instant }?.let { tidligereStønadsvedtak ->
        this.hentGjeldendeVedtaksdata(
            periode = tidligereStønadsvedtak.periode,
            clock = clock,
        ).mapLeft {
            FeilVedHentingAvGjeldendeVedtaksdataForPeriode.GjeldendeVedtaksdataFinnesIkke
        }.map {
            tidligereStønadsvedtak.periode to it.grunnlagsdataOgVilkårsvurderinger
        }
    } ?: FeilVedHentingAvGjeldendeVedtaksdataForPeriode.GjeldendeVedtaksdataFinnesIkke.left()
}
