package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.revurdering.GjeldendeVedtaksdata
import java.time.LocalDate
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
    fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak>
    fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak>
    fun opprettSak(sak: NySak)
    fun kopierGjeldendeVedtaksdata(sakId: UUID, fraOgMed: LocalDate): Either<KunneIkkeKopiereGjeldendeVedtaksdata, GjeldendeVedtaksdata>
}

sealed class KunneIkkeKopiereGjeldendeVedtaksdata {
    object FantIkkeSak : KunneIkkeKopiereGjeldendeVedtaksdata()
    object FantIngenVedtak : KunneIkkeKopiereGjeldendeVedtaksdata()
    data class UgyldigPeriode(val cause: Periode.UgyldigPeriode) : KunneIkkeKopiereGjeldendeVedtaksdata()
}

object FantIkkeSak
