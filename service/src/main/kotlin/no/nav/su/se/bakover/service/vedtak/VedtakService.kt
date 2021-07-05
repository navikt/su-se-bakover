package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import java.time.LocalDate
import java.util.UUID

interface VedtakService {
    fun hentAktiveFnr(fomDato: LocalDate): List<Fnr>
    fun kopierGjeldendeVedtaksdata(sakId: UUID, fraOgMed: LocalDate): Either<KunneIkkeKopiereGjeldendeVedtaksdata, GjeldendeVedtaksdata>
    fun hentTidligereGrunnlagsdataForVedtak(sakId: UUID, vedtakId: UUID): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata>
}

sealed class KunneIkkeKopiereGjeldendeVedtaksdata {
    object FantIkkeSak : KunneIkkeKopiereGjeldendeVedtaksdata()
    object FantIngenVedtak : KunneIkkeKopiereGjeldendeVedtaksdata()
    data class UgyldigPeriode(val cause: Periode.UgyldigPeriode) : KunneIkkeKopiereGjeldendeVedtaksdata()
}

sealed class KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak {
    object FantIkkeVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()
    object IngenTidligereVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()
}
