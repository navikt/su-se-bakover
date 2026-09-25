package no.nav.su.se.bakover.domain.historisk.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import java.util.UUID

@JvmInline
value class HistoriskInfotrygdRevurderingId(val value: UUID) {
    companion object {
        fun generer(): HistoriskInfotrygdRevurderingId = HistoriskInfotrygdRevurderingId(UUID.randomUUID())
    }
}

data class HistoriskInfotrygdRevurdering(
    val id: HistoriskInfotrygdRevurderingId,
    val sakId: UUID,
    val projeksjonId: UUID,
    val periode: Periode,
    val status: HistoriskInfotrygdRevurderingStatus,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val versjon: Long,
    val opprettet: Tidspunkt,
    val oppdatert: Tidspunkt,
    val begrunnelse: String?,
    val vedtaksbrevvalg: HistoriskInfotrygdVedtaksbrevvalg,
    val vedtaksbrevFritekst: String?,
    val vedtakSomRevurderesMånedsvis: HistoriskeVedtakSomRevurderesMånedsvis,
    val beregning: HistoriskInfotrygdBeregning?,
    val attesteringer: List<HistoriskInfotrygdAttestering>,
) {
    init {
        require(periode.måneder() == vedtakSomRevurderesMånedsvis.keys.toList()) {
            "Vedtak som revurderes må inneholde alle månedene i behandlingsperioden i stigende rekkefølge"
        }
        require(versjon >= 0) { "Versjon kan ikke være negativ" }
    }

    val erÅpen: Boolean
        get() = status in åpneStatuser

    fun oppdaterGrunnlag(
        begrunnelse: String,
        beregning: HistoriskInfotrygdBeregning?,
        saksbehandler: NavIdentBruker.Saksbehandler,
        tidspunkt: Tidspunkt,
    ): Either<KunneIkkeOppdatereHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
        if (status !in redigerbareStatuser) {
            return KunneIkkeOppdatereHistoriskInfotrygdRevurdering.UgyldigStatus(status).left()
        }
        if (begrunnelse.isBlank()) {
            return KunneIkkeOppdatereHistoriskInfotrygdRevurdering.ManglerBegrunnelse.left()
        }
        return copy(
            status = if (beregning == null) {
                HistoriskInfotrygdRevurderingStatus.OPPRETTET
            } else {
                HistoriskInfotrygdRevurderingStatus.BEREGNET
            },
            saksbehandler = saksbehandler,
            versjon = versjon + 1,
            oppdatert = tidspunkt,
            begrunnelse = begrunnelse,
            beregning = beregning,
        ).right()
    }

    fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        tidspunkt: Tidspunkt,
    ): Either<KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering, HistoriskInfotrygdRevurdering> {
        if (status !in redigerbareStatuser) {
            return KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.UgyldigStatus(status).left()
        }
        if (beregning == null) {
            return KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.ManglerBeregning.left()
        }
        if (beregning.månedsresultater.keys.toList() != periode.måneder()) {
            return KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering
                .BeregningDekkerIkkeHelePerioden
                .left()
        }
        if (begrunnelse.isNullOrBlank()) {
            return KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.ManglerBegrunnelse.left()
        }
        if (vedtaksbrevvalg == HistoriskInfotrygdVedtaksbrevvalg.IKKE_VALGT) {
            return KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.ManglerVedtaksbrevvalg.left()
        }
        if (
            vedtaksbrevvalg == HistoriskInfotrygdVedtaksbrevvalg.SEND &&
            vedtaksbrevFritekst.isNullOrBlank()
        ) {
            return KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.ManglerFritekstTilVedtaksbrev.left()
        }
        return copy(
            status = HistoriskInfotrygdRevurderingStatus.TIL_ATTESTERING,
            saksbehandler = saksbehandler,
            versjon = versjon + 1,
            oppdatert = tidspunkt,
        ).right()
    }

    fun underkjenn(
        attestant: NavIdentBruker.Attestant,
        begrunnelse: String,
        tidspunkt: Tidspunkt,
    ): Either<KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
        if (status != HistoriskInfotrygdRevurderingStatus.TIL_ATTESTERING) {
            return KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering.UgyldigStatus(status).left()
        }
        if (attestant.navIdent == saksbehandler.navIdent) {
            return KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering.SammeSaksbehandlerOgAttestant.left()
        }
        if (begrunnelse.isBlank()) {
            return KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering.ManglerBegrunnelse.left()
        }
        return copy(
            status = HistoriskInfotrygdRevurderingStatus.UNDERKJENT,
            versjon = versjon + 1,
            oppdatert = tidspunkt,
            attesteringer = attesteringer + HistoriskInfotrygdAttestering.Underkjent(
                attestant = attestant,
                begrunnelse = begrunnelse,
                tidspunkt = tidspunkt,
            ),
        ).right()
    }

    fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunkt: Tidspunkt,
    ): Either<KunneIkkeAvslutteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
        if (!erÅpen) {
            return KunneIkkeAvslutteHistoriskInfotrygdRevurdering.AlleredeAvsluttet.left()
        }
        if (begrunnelse.isBlank()) {
            return KunneIkkeAvslutteHistoriskInfotrygdRevurdering.ManglerBegrunnelse.left()
        }
        return copy(
            status = HistoriskInfotrygdRevurderingStatus.AVSLUTTET,
            saksbehandler = saksbehandler,
            versjon = versjon + 1,
            oppdatert = tidspunkt,
            begrunnelse = begrunnelse,
        ).right()
    }

    fun oppdaterVedtaksbrev(
        valg: HistoriskInfotrygdVedtaksbrevvalg.Valgt,
        fritekst: String?,
        saksbehandler: NavIdentBruker.Saksbehandler,
        tidspunkt: Tidspunkt,
    ): Either<KunneIkkeOppdatereHistoriskInfotrygdVedtaksbrev, HistoriskInfotrygdRevurdering> {
        if (status !in redigerbareStatuser) {
            return KunneIkkeOppdatereHistoriskInfotrygdVedtaksbrev.UgyldigStatus(status).left()
        }
        return copy(
            vedtaksbrevvalg = valg,
            vedtaksbrevFritekst = fritekst,
            saksbehandler = saksbehandler,
            versjon = versjon + 1,
            oppdatert = tidspunkt,
        ).right()
    }

    fun forsøkIverksettelse(): Either<KunneIkkeIverksetteHistoriskInfotrygdRevurdering, Nothing> =
        KunneIkkeIverksetteHistoriskInfotrygdRevurdering.UtbetalingsdesignIkkeAvklart.left()

    fun verifiserAtVedtakeneSomRevurderesIkkeHarForandretSeg(
        gjeldendeVedtaksdata: GjeldendeHistoriskInfotrygdVedtaksdata,
    ): Either<KunneIkkeVerifisereHistoriskeVedtakSomRevurderes, Unit> {
        val gjeldendeVedtak = gjeldendeVedtaksdata
            .vedtakSomRevurderes(periode)
            .mapLeft(KunneIkkeVerifisereHistoriskeVedtakSomRevurderes::UgyldigGjeldendeVedtaksdata)
            .getOrElse { return it.left() }

        return if (gjeldendeVedtak == vedtakSomRevurderesMånedsvis) {
            Unit.right()
        } else {
            KunneIkkeVerifisereHistoriskeVedtakSomRevurderes.DetHarKommetNyeOverlappendeVedtak.left()
        }
    }

    companion object {
        fun opprett(
            id: HistoriskInfotrygdRevurderingId = HistoriskInfotrygdRevurderingId.generer(),
            sakId: UUID,
            projeksjonId: UUID,
            periode: Periode,
            saksbehandler: NavIdentBruker.Saksbehandler,
            tidspunkt: Tidspunkt,
            gjeldendeVedtaksdata: GjeldendeHistoriskInfotrygdVedtaksdata,
        ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
            if (gjeldendeVedtaksdata.projeksjonId != projeksjonId) {
                return KunneIkkeOppretteHistoriskInfotrygdRevurdering.FeilProjeksjon.left()
            }
            val vedtakSomRevurderes = gjeldendeVedtaksdata
                .vedtakSomRevurderes(periode)
                .getOrElse { return it.left() }
            return HistoriskInfotrygdRevurdering(
                id = id,
                sakId = sakId,
                projeksjonId = projeksjonId,
                periode = periode,
                status = HistoriskInfotrygdRevurderingStatus.OPPRETTET,
                saksbehandler = saksbehandler,
                versjon = 0,
                opprettet = tidspunkt,
                oppdatert = tidspunkt,
                begrunnelse = null,
                vedtaksbrevvalg = HistoriskInfotrygdVedtaksbrevvalg.IKKE_VALGT,
                vedtaksbrevFritekst = null,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderes,
                beregning = null,
                attesteringer = emptyList(),
            ).right()
        }
    }
}

enum class HistoriskInfotrygdRevurderingStatus {
    OPPRETTET,
    BEREGNET,
    TIL_ATTESTERING,
    UNDERKJENT,
    AVSLUTTET,
}

sealed interface HistoriskInfotrygdVedtaksbrevvalg {
    sealed interface Valgt : HistoriskInfotrygdVedtaksbrevvalg

    data object IKKE_VALGT : HistoriskInfotrygdVedtaksbrevvalg
    data object SEND : Valgt
    data object IKKE_SEND : Valgt
}

data class HistoriskInfotrygdBeregning(
    val månedsresultater: Map<Måned, HistoriskInfotrygdRevurdertMånedsresultat>,
    override val benyttetRegel: Regelspesifisering,
) : RegelspesifisertBeregning {
    init {
        require(månedsresultater.isNotEmpty()) { "Beregningen må inneholde minst én måned" }
        require(månedsresultater.keys.toList() == månedsresultater.keys.sorted()) {
            "Beregningsmånedene må ligge i stigende rekkefølge"
        }
        require(månedsresultater.all { (måned, resultat) -> måned == resultat.måned }) {
            "Nøkkelen må være lik måneden i beregningsresultatet"
        }
    }
}

sealed interface HistoriskInfotrygdAttestering {
    val attestant: NavIdentBruker.Attestant
    val tidspunkt: Tidspunkt

    data class Underkjent(
        override val attestant: NavIdentBruker.Attestant,
        val begrunnelse: String,
        override val tidspunkt: Tidspunkt,
    ) : HistoriskInfotrygdAttestering
}

data class HistoriskeVedtakSomRevurderesMånedsvis(
    val value: Map<Måned, HistoriskVedtakSomRevurderes>,
) : Map<Måned, HistoriskVedtakSomRevurderes> by value

sealed interface HistoriskVedtakSomRevurderes {
    data class OriginaltInfotrygdVedtak(
        val vedtakId: HistoriskVedtakId,
    ) : HistoriskVedtakSomRevurderes

    data class HistoriskRevurderingsvedtak(
        val vedtakId: HistoriskInfotrygdRevurderingsvedtakId,
    ) : HistoriskVedtakSomRevurderes
}

private fun GjeldendeHistoriskInfotrygdVedtaksdata.vedtakSomRevurderes(
    periode: Periode,
): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskeVedtakSomRevurderesMånedsvis> =
    periode.måneder().associateWithTo(linkedMapOf()) { måned ->
        forMåned(måned)?.tilVedtakSomRevurderes()
            ?: return KunneIkkeOppretteHistoriskInfotrygdRevurdering.ManglerGjeldendeData(måned).left()
    }.let(::HistoriskeVedtakSomRevurderesMånedsvis).right()

private fun GjeldendeHistoriskInfotrygdMånedsdata.tilVedtakSomRevurderes(): HistoriskVedtakSomRevurderes? =
    when (val kilde = kilde) {
        is HistoriskInfotrygdMånedskilde.OriginalProjeksjon -> {
            val opprinneligVedtakId = when (this) {
                is GjeldendeHistoriskInfotrygdMånedsdata.Ytelse -> opprinneligVedtakId
                is GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse -> opprinneligVedtakId
            }
            opprinneligVedtakId?.let { HistoriskVedtakSomRevurderes.OriginaltInfotrygdVedtak(it) }
        }

        is HistoriskInfotrygdMånedskilde.Revurderingsvedtak ->
            HistoriskVedtakSomRevurderes.HistoriskRevurderingsvedtak(kilde.vedtakId)
    }

sealed interface KunneIkkeOppretteHistoriskInfotrygdRevurdering {
    data object FeilProjeksjon : KunneIkkeOppretteHistoriskInfotrygdRevurdering
    data class ManglerGjeldendeData(val måned: Måned) : KunneIkkeOppretteHistoriskInfotrygdRevurdering
    data class OverlapperÅpenBehandling(
        val eksisterendeRevurderingId: HistoriskInfotrygdRevurderingId,
        val sakId: UUID,
    ) : KunneIkkeOppretteHistoriskInfotrygdRevurdering
}

sealed interface KunneIkkeVerifisereHistoriskeVedtakSomRevurderes {
    data class UgyldigGjeldendeVedtaksdata(
        val feil: KunneIkkeOppretteHistoriskInfotrygdRevurdering,
    ) : KunneIkkeVerifisereHistoriskeVedtakSomRevurderes

    data object DetHarKommetNyeOverlappendeVedtak : KunneIkkeVerifisereHistoriskeVedtakSomRevurderes
}

sealed interface KunneIkkeOppdatereHistoriskInfotrygdRevurdering {
    data class UgyldigStatus(
        val status: HistoriskInfotrygdRevurderingStatus,
    ) : KunneIkkeOppdatereHistoriskInfotrygdRevurdering

    data object ManglerBegrunnelse : KunneIkkeOppdatereHistoriskInfotrygdRevurdering
}

sealed interface KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering {
    data class UgyldigStatus(
        val status: HistoriskInfotrygdRevurderingStatus,
    ) : KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering

    data object ManglerBeregning : KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering
    data object BeregningDekkerIkkeHelePerioden :
        KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering

    data object ManglerBegrunnelse : KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering
    data object ManglerVedtaksbrevvalg : KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering
    data object ManglerFritekstTilVedtaksbrev :
        KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering
}

sealed interface KunneIkkeOppdatereHistoriskInfotrygdVedtaksbrev {
    data class UgyldigStatus(
        val status: HistoriskInfotrygdRevurderingStatus,
    ) : KunneIkkeOppdatereHistoriskInfotrygdVedtaksbrev
}

sealed interface KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering {
    data class UgyldigStatus(
        val status: HistoriskInfotrygdRevurderingStatus,
    ) : KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering

    data object SammeSaksbehandlerOgAttestant : KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering
    data object ManglerBegrunnelse : KunneIkkeUnderkjenneHistoriskInfotrygdRevurdering
}

sealed interface KunneIkkeAvslutteHistoriskInfotrygdRevurdering {
    data object AlleredeAvsluttet : KunneIkkeAvslutteHistoriskInfotrygdRevurdering
    data object ManglerBegrunnelse : KunneIkkeAvslutteHistoriskInfotrygdRevurdering
}

sealed interface KunneIkkeIverksetteHistoriskInfotrygdRevurdering {
    data object UtbetalingsdesignIkkeAvklart : KunneIkkeIverksetteHistoriskInfotrygdRevurdering
}

private val redigerbareStatuser = setOf(
    HistoriskInfotrygdRevurderingStatus.OPPRETTET,
    HistoriskInfotrygdRevurderingStatus.BEREGNET,
    HistoriskInfotrygdRevurderingStatus.UNDERKJENT,
)

private val åpneStatuser = redigerbareStatuser + HistoriskInfotrygdRevurderingStatus.TIL_ATTESTERING
