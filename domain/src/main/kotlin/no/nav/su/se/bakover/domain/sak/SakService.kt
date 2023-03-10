package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.AlleredeGjeldendeSakForBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
    fun hentSak(sakId: UUID, sessionContext: SessionContext): Either<FantIkkeSak, Sak>
    fun hentSak(fnr: Fnr, type: Sakstype): Either<FantIkkeSak, Sak>
    fun hentSaker(fnr: Fnr): Either<FantIkkeSak, List<Sak>>
    fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak>
    fun hentGjeldendeVedtaksdata(
        sakId: UUID,
        periode: Periode,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata?>

    /**
     * @see [Sak.historiskGrunnlagForVedtaketsPeriode]
     */
    fun historiskGrunnlagForVedtaketsPeriode(
        sakId: UUID,
        vedtakId: UUID,
    ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata>

    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingssammendrag>
    fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingssammendrag>
    fun hentAlleredeGjeldendeSakForBruker(fnr: Fnr): AlleredeGjeldendeSakForBruker
    fun hentSakidOgSaksnummer(fnr: Fnr): Either<FantIkkeSak, SakInfo>
    fun hentSakInfo(sakId: UUID): Either<FantIkkeSak, SakInfo>

    fun hentSakForRevurdering(revurderingId: UUID): Sak

    fun hentSakForRevurdering(revurderingId: UUID, sessionContext: SessionContext): Sak

    fun hentSakForSøknadsbehandling(søknadsbehandlingId: UUID): Sak

    fun hentSakForSøknad(søknadId: UUID): Either<FantIkkeSak, Sak>
    fun opprettFritekstDokument(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.UtenMetadata>
    fun lagreOgSendFritekstDokument(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.MedMetadata>
}

object FantIkkeSak {
    override fun toString() = this::class.simpleName!!
}

sealed class KunneIkkeHenteGjeldendeVedtaksdata {
    object FantIkkeSak : KunneIkkeHenteGjeldendeVedtaksdata()
    object IngenVedtak : KunneIkkeHenteGjeldendeVedtaksdata()
}

sealed class KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak {
    data class Feil(val feil: Sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak) :
        KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()

    object FantIkkeSak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()
}

data class OpprettDokumentRequest(
    val sakId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val tittel: String,
    val fritekst: String,
)

sealed interface KunneIkkeOppretteDokument {
    data class KunneIkkeLageDokument(val feil: no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument) :
        KunneIkkeOppretteDokument

    data class FeilVedHentingAvSaksbehandlernavn(val feil: KunneIkkeHenteNavnForNavIdent) : KunneIkkeOppretteDokument
}
