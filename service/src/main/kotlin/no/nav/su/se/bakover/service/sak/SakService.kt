package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.BegrensetSakerInfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknadstype
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
    fun hentSak(fnr: Fnr, type: Søknadstype): Either<FantIkkeSak, Sak>
    fun hentSaker(fnr: Fnr): Either<FantIkkeSak, List<Sak>>
    fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak>
    fun hentGjeldendeVedtaksdata(
        sakId: UUID,
        periode: Periode,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata?>

    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingsoversikt>
    fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingsoversikt>
    fun hentBegrensetSakerInfo(fnr: Fnr): Either<FantIkkeSak, BegrensetSakerInfo>
    fun hentSakidOgSaksnummer(fnr: Fnr): Either<FantIkkeSak, SakInfo>
}

object FantIkkeSak

sealed class KunneIkkeHenteGjeldendeVedtaksdata {
    object FantIkkeSak : KunneIkkeHenteGjeldendeVedtaksdata()
    object IngenVedtak : KunneIkkeHenteGjeldendeVedtaksdata()
}
