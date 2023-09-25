package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakInfo
import java.time.Clock
import java.util.UUID

data class AvsluttetRevurdering private constructor(
    val underliggendeRevurdering: Revurdering,
    val begrunnelse: String,
    /** Denne er ikke låst til [Brevvalg.SaksbehandlersValg] siden det avhenger av om det er forhåndsvarslet eller ikke. Dette ble også migrert på et tidspunkt, tidligere ble det alltid sendt brev dersom det var forhåndsvarslet. */
    val brevvalg: Brevvalg,
    override val avsluttetTidspunkt: Tidspunkt,
    override val avsluttetAv: NavIdentBruker?,
) : Revurdering(),
    Avbrutt {

    override val id: UUID = underliggendeRevurdering.id
    override val opprettet: Tidspunkt = underliggendeRevurdering.opprettet
    override val oppdatert: Tidspunkt = underliggendeRevurdering.oppdatert
    override val periode: Periode = underliggendeRevurdering.periode
    override val tilRevurdering: UUID = underliggendeRevurdering.tilRevurdering
    override val sakinfo: SakInfo = underliggendeRevurdering.sakinfo
    override val grunnlagsdataOgVilkårsvurderinger = underliggendeRevurdering.grunnlagsdataOgVilkårsvurderinger

    /** se egne valg for brev for avslutting [Brevvalg] */
    override val brevvalgRevurdering: BrevvalgRevurdering = underliggendeRevurdering.brevvalgRevurdering

    // TODO jah: Denne bør overstyres av saksbehandler som avsluttet revurderingen.
    override val saksbehandler: NavIdentBruker.Saksbehandler = underliggendeRevurdering.saksbehandler
    override val revurderingsårsak: Revurderingsårsak = underliggendeRevurdering.revurderingsårsak
    override val informasjonSomRevurderes: InformasjonSomRevurderes = underliggendeRevurdering.informasjonSomRevurderes
    override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis =
        underliggendeRevurdering.vedtakSomRevurderesMånedsvis
    override val oppgaveId: OppgaveId = underliggendeRevurdering.oppgaveId
    override val attesteringer: Attesteringshistorikk = underliggendeRevurdering.attesteringer
    override val erOpphørt: Boolean = underliggendeRevurdering.erOpphørt

    override val avkorting: AvkortingVedRevurdering = when (val avkorting = underliggendeRevurdering.avkorting) {
        is AvkortingVedRevurdering.DelvisHåndtert -> {
            avkorting.kanIkke()
        }

        is AvkortingVedRevurdering.Håndtert -> {
            avkorting.kanIkke()
        }

        is AvkortingVedRevurdering.Iverksatt -> {
            throw IllegalStateException("Kan ikke avslutte iverksatt")
        }

        is AvkortingVedRevurdering.Uhåndtert -> {
            avkorting.kanIkke()
        }
    }

    override fun skalTilbakekreve() = false

    override val beregning = underliggendeRevurdering.beregning

    override val simulering = underliggendeRevurdering.simulering

    /**
     * Sender et avsluttningsbrev
     */
    override fun skalSendeVedtaksbrev(): Boolean {
        return skalSendeAvslutningsbrev()
    }

    override fun erÅpen() = false

    fun skalSendeAvslutningsbrev(): Boolean {
        return brevvalg.skalSendeBrev()
    }

    companion object {
        fun tryCreate(
            underliggendeRevurdering: Revurdering,
            begrunnelse: String,
            brevvalg: Brevvalg?,
            tidspunktAvsluttet: Tidspunkt,
            avsluttetAv: NavIdentBruker?,
        ): Either<KunneIkkeLageAvsluttetRevurdering, AvsluttetRevurdering> {
            return when (underliggendeRevurdering) {
                is IverksattRevurdering -> KunneIkkeLageAvsluttetRevurdering.RevurderingenErIverksatt.left()

                is RevurderingTilAttestering -> KunneIkkeLageAvsluttetRevurdering.RevurderingenErTilAttestering.left()
                is AvsluttetRevurdering -> KunneIkkeLageAvsluttetRevurdering.RevurderingErAlleredeAvsluttet.left()

                is OpprettetRevurdering,
                is BeregnetRevurdering,
                is SimulertRevurdering,
                is UnderkjentRevurdering,
                -> {
                    AvsluttetRevurdering(
                        underliggendeRevurdering,
                        begrunnelse,
                        // TODO jah: Endre navn på Brevvalg til å hete AvsluttetBrevvalg og flytt inn i tilhørende mappe.
                        //  Det er litt uheldig at vi kan avslutte uten brevvalg, ved "vis brev".
                        brevvalg ?: Brevvalg.SkalIkkeSendeBrev("IKKE_FORHÅNDSVARSLET"),
                        tidspunktAvsluttet,
                        avsluttetAv,
                    ).right()
                }
            }
        }
    }

    override fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> = emptyList()
}

sealed class KunneIkkeLageAvsluttetRevurdering {
    data object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetRevurdering()
    data object RevurderingenErIverksatt : KunneIkkeLageAvsluttetRevurdering()
    data object RevurderingenErTilAttestering : KunneIkkeLageAvsluttetRevurdering()
    data object BrevvalgUtenForhåndsvarsel : KunneIkkeLageAvsluttetRevurdering()
    data object ManglerBrevvalgVedForhåndsvarsling : KunneIkkeLageAvsluttetRevurdering()
}

sealed class KunneIkkeAvslutteRevurdering {
    data class KunneIkkeLageAvsluttetRevurdering(
        val feil: no.nav.su.se.bakover.domain.revurdering.KunneIkkeLageAvsluttetRevurdering,
    ) : KunneIkkeAvslutteRevurdering()

    data class KunneIkkeLageAvsluttetGjenopptaAvYtelse(
        val feil: no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeLageAvsluttetGjenopptaAvYtelse,
    ) : KunneIkkeAvslutteRevurdering()

    data class KunneIkkeLageAvsluttetStansAvYtelse(
        val feil: StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse,
    ) : KunneIkkeAvslutteRevurdering()

    data object FantIkkeRevurdering : KunneIkkeAvslutteRevurdering()
    data object KunneIkkeLageDokument : KunneIkkeAvslutteRevurdering()
    data object FantIkkePersonEllerSaksbehandlerNavn : KunneIkkeAvslutteRevurdering()
    data object BrevvalgIkkeTillatt : KunneIkkeAvslutteRevurdering()
}
