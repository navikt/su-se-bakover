package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.journalføring.Journalpost
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.AlleredeGjeldendeSakForBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.sak.fnr.KunneIkkeOppdatereFødselsnummer
import no.nav.su.se.bakover.domain.sak.fnr.OppdaterFødselsnummerPåSakCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import person.domain.KunneIkkeHenteNavnForNavIdent
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
    fun hentSak(sakId: UUID, sessionContext: SessionContext): Either<FantIkkeSak, Sak>
    fun hentSak(fnr: Fnr, type: Sakstype): Either<FantIkkeSak, Sak>
    fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak>
    fun hentSakForUtbetalingId(utbetalingId: UUID30): Either<FantIkkeSak, Sak>
    fun hentSaker(fnr: Fnr): Either<FantIkkeSak, List<Sak>>

    fun hentSak(hendelseId: HendelseId): Either<FantIkkeSak, Sak>
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

    fun hentSakForRevurdering(revurderingId: RevurderingId): Sak

    fun hentSakForRevurdering(revurderingId: RevurderingId, sessionContext: SessionContext): Sak

    fun hentSakForSøknadsbehandling(søknadsbehandlingId: SøknadsbehandlingId): Sak

    fun hentSakForVedtak(vedtakId: UUID): Sak?

    fun hentSakForSøknad(søknadId: UUID): Either<FantIkkeSak, Sak>
    fun opprettFritekstDokument(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.UtenMetadata>
    fun genererLagreOgSendFritekstDokument(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.MedMetadata>
    fun lagreOgSendFritekstDokument(request: JournalførOgSendDokumentCommand): Dokument.MedMetadata
    fun hentAlleJournalposter(sakId: UUID): Either<KunneIkkeHenteJournalposter, List<Journalpost>>
    fun oppdaterFødselsnummer(command: OppdaterFødselsnummerPåSakCommand): Either<KunneIkkeOppdatereFødselsnummer, Sak>

    fun hentSakIdSaksnummerOgFnrForAlleSaker(): List<SakInfo>
}

data object FantIkkeSak

sealed interface KunneIkkeHenteGjeldendeVedtaksdata {
    data object FantIkkeSak : KunneIkkeHenteGjeldendeVedtaksdata
    data object IngenVedtak : KunneIkkeHenteGjeldendeVedtaksdata
}

sealed interface KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak {
    data class Feil(
        val feil: Sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak,
    ) : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak

    data object FantIkkeSak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
}

data class OpprettDokumentRequest(
    val sakId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val tittel: String,
    val fritekst: String,
    val distribueringsadresse: Distribueringsadresse?,
    val distribusjonstype: Distribusjonstype,
)

sealed interface KunneIkkeOppretteDokument {
    data class KunneIkkeLageDokument(val feil: dokument.domain.KunneIkkeLageDokument) :
        KunneIkkeOppretteDokument

    data class FeilVedHentingAvSaksbehandlernavn(val feil: KunneIkkeHenteNavnForNavIdent) : KunneIkkeOppretteDokument
}
