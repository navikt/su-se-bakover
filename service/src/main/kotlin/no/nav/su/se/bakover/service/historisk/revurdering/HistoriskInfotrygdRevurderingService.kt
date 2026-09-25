package no.nav.su.se.bakover.service.historisk.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.SakInfoNy
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdYtelseForMåned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.OriginalHistoriskInfotrygdYtelsestidslinje
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingRepo
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdVedtaksbrevvalg
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.brev.KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
import no.nav.su.se.bakover.domain.historisk.revurdering.brev.lagVedtaksbrevkommando
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import satser.domain.SatsFactory
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

class HistoriskInfotrygdRevurderingService(
    private val sakRepo: SakRepo,
    private val historiskAlderProjeksjonRepo: HistoriskAlderProjeksjonRepo,
    private val revurderingRepo: HistoriskInfotrygdRevurderingRepo,
    private val førsteInnvilgedeSuAppMåned: FørsteInnvilgedeSuAppMåned,
    private val brevService: BrevService,
    private val satsFactory: SatsFactory,
    private val clock: Clock,
) {
    fun opprett(
        command: OpprettHistoriskInfotrygdRevurderingCommand,
    ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurderingService, HistoriskInfotrygdRevurdering> {
        if (!command.periode.erHeleMåneder()) {
            return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .PeriodenMåBeståAvHeleMåneder
                .left()
        }
        val sak = hentEllerOpprettAlderssak(command.fnr)
        val førsteInnvilgedeMåned = førsteInnvilgedeSuAppMåned.hent(sak.sakId)
        if (førsteInnvilgedeMåned != null && command.periode.tilOgMed >= førsteInnvilgedeMåned.fraOgMed) {
            return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .OverlapperInnvilgetSuAppYtelse(førsteInnvilgedeMåned)
                .left()
        }

        val projeksjonId = historiskAlderProjeksjonRepo
            .hentSisteFullførteProjeksjonIdForPerson(command.fnr.value)
            ?: return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .FantIngenFullførtHistoriskProjeksjon
                .left()
        val grunnlag = historiskAlderProjeksjonRepo.hentOriginalTidslinjegrunnlag(
            projeksjonId = projeksjonId,
            personident = command.fnr.value,
            periode = command.periode,
        )
        val original = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = command.periode,
            grunnlag = grunnlag,
        )
        val førsteMånedUtenHistoriskVedtak = original.måneder
            .entries
            .firstOrNull { (_, ytelse) -> !ytelse.harHistoriskVedtak() }
            ?.key
        if (førsteMånedUtenHistoriskVedtak != null) {
            return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .MånedManglerHistoriskVedtak(førsteMånedUtenHistoriskVedtak)
                .left()
        }
        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata.bygg(
            original = original,
            effekter = emptyList(),
        )
        val revurdering = HistoriskInfotrygdRevurdering.opprett(
            sakId = sak.sakId,
            projeksjonId = projeksjonId,
            periode = command.periode,
            saksbehandler = command.saksbehandler,
            tidspunkt = Tidspunkt.now(clock),
            gjeldendeVedtaksdata = gjeldende,
        ).fold(
            ifLeft = {
                return KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                    .UgyldigHistoriskGrunnlag(it.toString())
                    .left()
            },
            ifRight = { it },
        )

        return revurderingRepo.opprett(revurdering).mapLeft {
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.LagringFeilet(it)
        }
    }

    fun hent(id: HistoriskInfotrygdRevurderingId) = revurderingRepo.hent(id)

    fun hentVedtakForUtbetaling(utbetalingId: UUID30) =
        revurderingRepo.hentVedtakForUtbetaling(utbetalingId)

    fun hentMedSakInfo(
        id: HistoriskInfotrygdRevurderingId,
    ): Pair<SakInfo, HistoriskInfotrygdRevurdering>? {
        val revurdering = revurderingRepo.hent(id) ?: return null
        val sakInfo = sakRepo.hentSakInfo(revurdering.sakId) ?: return null
        return sakInfo to revurdering
    }

    fun sendTilAttestering(
        id: HistoriskInfotrygdRevurderingId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.sendTilAttestering(
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun underkjenn(
        id: HistoriskInfotrygdRevurderingId,
        attestant: NavIdentBruker.Attestant,
        begrunnelse: String,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.underkjenn(
                attestant = attestant,
                begrunnelse = begrunnelse,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun avslutt(
        id: HistoriskInfotrygdRevurderingId,
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.avslutt(
                saksbehandler = saksbehandler,
                begrunnelse = begrunnelse,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun oppdaterVedtaksbrev(
        id: HistoriskInfotrygdRevurderingId,
        valg: HistoriskInfotrygdVedtaksbrevvalg.Valgt,
        fritekst: String?,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        endre(id) { eksisterende ->
            eksisterende.oppdaterVedtaksbrev(
                valg = valg,
                fritekst = fritekst,
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),
            ).mapLeft { KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand(it.toString()) }
        }

    fun lagVedtaksbrevutkast(
        id: HistoriskInfotrygdRevurderingId,
    ): Either<KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast, PdfA> {
        val (sakInfo, revurdering) = hentMedSakInfo(id)
            ?: return KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.FantIkkeBehandling.left()
        if (revurdering.vedtaksbrevvalg != HistoriskInfotrygdVedtaksbrevvalg.SEND) {
            return KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.SkalIkkeSendeBrev.left()
        }
        return revurdering.lagVedtaksbrevkommando(
            sakInfo = sakInfo,
            satsFactory = satsFactory,
        ).mapLeft {
            KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.KunneIkkeLageBrevgrunnlag(it)
        }.flatMap { command ->
            brevService.lagDokumentPdf(command).mapLeft {
                KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.KunneIkkeGenererePdf(it)
            }.map { it.generertDokument }
        }
    }

    private fun endre(
        id: HistoriskInfotrygdRevurderingId,
        endring: (
            HistoriskInfotrygdRevurdering,
        ) -> Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering>,
    ): Either<KunneIkkeEndreHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
        val eksisterende = revurderingRepo.hent(id)
            ?: return KunneIkkeEndreHistoriskInfotrygdRevurdering.FantIkkeBehandling.left()
        return endring(eksisterende).flatMap { oppdatert ->
            if (revurderingRepo.lagre(oppdatert, forventetVersjon = eksisterende.versjon)) {
                oppdatert.right()
            } else {
                KunneIkkeEndreHistoriskInfotrygdRevurdering.Versjonskonflikt.left()
            }
        }
    }

    private fun hentEllerOpprettAlderssak(fnr: Fnr): SakInfo {
        sakRepo.hentSakInfoForIdent(fnr, Sakstype.ALDER)?.let { return it }
        val nySak = SakInfoNy(
            sakId = UUID.randomUUID(),
            fnr = fnr,
            type = Sakstype.ALDER,
        )
        sakRepo.opprettSak(nySak)
        return requireNotNull(sakRepo.hentSakInfoForIdent(fnr, Sakstype.ALDER)) {
            "Fant ikke alderssak etter opprettelse"
        }
    }
}

data class OpprettHistoriskInfotrygdRevurderingCommand(
    val fnr: Fnr,
    val periode: Periode,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)

sealed interface KunneIkkeOppretteHistoriskInfotrygdRevurderingService {
    data object FantIngenFullførtHistoriskProjeksjon : KunneIkkeOppretteHistoriskInfotrygdRevurderingService
    data object PeriodenMåBeståAvHeleMåneder : KunneIkkeOppretteHistoriskInfotrygdRevurderingService
    data class MånedManglerHistoriskVedtak(val måned: Måned) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService

    data class OverlapperInnvilgetSuAppYtelse(val førsteInnvilgedeMåned: Måned) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService

    data class UgyldigHistoriskGrunnlag(val begrunnelse: String) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService

    data class LagringFeilet(val feil: KunneIkkeOppretteHistoriskInfotrygdRevurdering) : KunneIkkeOppretteHistoriskInfotrygdRevurderingService
}

sealed interface KunneIkkeEndreHistoriskInfotrygdRevurdering {
    data object FantIkkeBehandling : KunneIkkeEndreHistoriskInfotrygdRevurdering
    data object Versjonskonflikt : KunneIkkeEndreHistoriskInfotrygdRevurdering
    data class UgyldigTilstand(val begrunnelse: String) : KunneIkkeEndreHistoriskInfotrygdRevurdering
}

sealed interface KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast {
    data object FantIkkeBehandling : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
    data object SkalIkkeSendeBrev : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
    data class KunneIkkeLageBrevgrunnlag(
        val feil: KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando,
    ) : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
    data class KunneIkkeGenererePdf(
        val feil: KunneIkkeLageDokument,
    ) : KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
}

fun interface FørsteInnvilgedeSuAppMåned {
    fun hent(sakId: UUID): Måned?
}

class FørsteInnvilgedeSuAppMånedFraVedtak(
    private val vedtakRepo: VedtakRepo,
) : FørsteInnvilgedeSuAppMåned {
    override fun hent(sakId: UUID): Måned? =
        vedtakRepo.hentVedtakSomKanRevurderesForSak(sakId)
            .asSequence()
            .filter { it.erInnvilget() }
            .map { it.periode.fraOgMed }
            .minOrNull()
            ?.let { Måned.fra(YearMonth.from(it)) }
}

private fun HistoriskInfotrygdYtelseForMåned.harHistoriskVedtak(): Boolean = when (this) {
    is HistoriskInfotrygdYtelseForMåned.Ytelse -> true
    is HistoriskInfotrygdYtelseForMåned.IngenYtelse -> vedtakId != null
}

private fun Periode.erHeleMåneder(): Boolean =
    fraOgMed.dayOfMonth == 1 && tilOgMed == YearMonth.from(tilOgMed).atEndOfMonth()
