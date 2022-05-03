package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.BegrensetSakinfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.SakIdSaksnummerFnr
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
    fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak>
    fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak>
    fun hentGjeldendeVedtaksdata(sakId: UUID, periode: Periode): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata?>
    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingsoversikt>
    fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingsoversikt>
    fun hentBegrensetSakinfo(fnr: Fnr): Either<FantIkkeSak, BegrensetSakinfo>
    fun hentSakidOgSaksnummer(fnr: Fnr): Either<FantIkkeSak, SakIdSaksnummerFnr>
}

object FantIkkeSak

sealed class KunneIkkeHenteGjeldendeVedtaksdata {
    object UgyldigPeriode : KunneIkkeHenteGjeldendeVedtaksdata()
    object FantIkkeSak : KunneIkkeHenteGjeldendeVedtaksdata()
}
