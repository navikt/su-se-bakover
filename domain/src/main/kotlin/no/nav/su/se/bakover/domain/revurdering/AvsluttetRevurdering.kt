package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

data class AvsluttetRevurdering private constructor(
    val underliggendeRevurdering: Revurdering,
    val begrunnelse: String,
    /** Denne er ikke låst til [Brevvalg.SaksbehandlersValg] siden det avhenger av om det er forhåndsvarslet eller ikke. Dette ble også migrert på et tidspunkt, tidligere ble det alltid sendt brev dersom det var forhåndsvarslet. */
    val brevvalg: Brevvalg,
    val tidspunktAvsluttet: Tidspunkt,
) : Revurdering() {
    override val id: UUID = underliggendeRevurdering.id
    override val opprettet: Tidspunkt = underliggendeRevurdering.opprettet
    override val periode: Periode = underliggendeRevurdering.periode
    override val tilRevurdering: UUID = underliggendeRevurdering.tilRevurdering
    override val sakinfo: SakInfo = underliggendeRevurdering.sakinfo
    override val grunnlagsdata: Grunnlagsdata = underliggendeRevurdering.grunnlagsdata
    override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering = underliggendeRevurdering.vilkårsvurderinger

    // TODO jah: Denne bør overstyres av saksbehandler som avsluttet revurderingen.
    override val saksbehandler: NavIdentBruker.Saksbehandler = underliggendeRevurdering.saksbehandler
    override val fritekstTilBrev: String = underliggendeRevurdering.fritekstTilBrev
    override val revurderingsårsak: Revurderingsårsak = underliggendeRevurdering.revurderingsårsak
    override val informasjonSomRevurderes: InformasjonSomRevurderes =
        underliggendeRevurdering.informasjonSomRevurderes
    override val forhåndsvarsel: Forhåndsvarsel? = underliggendeRevurdering.forhåndsvarsel
    override val oppgaveId: OppgaveId = underliggendeRevurdering.oppgaveId
    override val attesteringer: Attesteringshistorikk = underliggendeRevurdering.attesteringer
    override val erOpphørt: Boolean = underliggendeRevurdering.erOpphørt

    init {
        when (forhåndsvarsel.harSendtForhåndsvarsel()) {
            false -> require(brevvalg is Brevvalg.SkalIkkeSendeBrev) {
                "Saksbehandler kan ikke gjøre et brevvalg dersom man skal avslutte en revurdering som ikke er forhåndsvarslet"
            }
            true -> require(brevvalg is Brevvalg.SaksbehandlersValg || brevvalg is Brevvalg.SkalSendeBrev) {
                "Saksbehandler må gjøre et brevvalg dersom man skal avslutte en revurdering som er forhåndsvarslet"
            }
        }
    }

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

    val beregning = when (underliggendeRevurdering) {
        is BeregnetRevurdering -> underliggendeRevurdering.beregning
        is SimulertRevurdering -> underliggendeRevurdering.beregning
        is UnderkjentRevurdering.Opphørt -> underliggendeRevurdering.beregning
        is UnderkjentRevurdering.Innvilget -> underliggendeRevurdering.beregning
        is UnderkjentRevurdering.IngenEndring -> underliggendeRevurdering.beregning

        is OpprettetRevurdering -> null

        is AvsluttetRevurdering,
        is RevurderingTilAttestering,
        is IverksattRevurdering,
        -> throw IllegalStateException("Skal ikke kunne instansiere en AvsluttetRevurdering med ${underliggendeRevurdering::class}. Sjekk tryCreate om du får denne feilen. id: $id")
    }

    val simulering = when (underliggendeRevurdering) {
        is SimulertRevurdering -> underliggendeRevurdering.simulering
        is UnderkjentRevurdering.Opphørt -> underliggendeRevurdering.simulering
        is UnderkjentRevurdering.Innvilget -> underliggendeRevurdering.simulering

        is BeregnetRevurdering,
        is UnderkjentRevurdering.IngenEndring,
        is OpprettetRevurdering,
        -> null

        is AvsluttetRevurdering,
        is RevurderingTilAttestering,
        is IverksattRevurdering,
        -> throw IllegalStateException("Skal ikke kunne instansiere en AvsluttetRevurdering med ${underliggendeRevurdering::class}. Sjekk tryCreate om du får denne feilen. id: $id")
    }

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun skalSendeAvslutningsbrev(): Boolean {
        return brevvalg.skalSendeBrev()
    }

    companion object {
        fun tryCreate(
            underliggendeRevurdering: Revurdering,
            begrunnelse: String,
            brevvalg: Brevvalg?,
            tidspunktAvsluttet: Tidspunkt,
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
                    if (underliggendeRevurdering.forhåndsvarsel.harSendtForhåndsvarsel() && brevvalg == null) {
                        KunneIkkeLageAvsluttetRevurdering.ManglerBrevvalgVedForhåndsvarsling.left()
                    }
                    if (!underliggendeRevurdering.forhåndsvarsel.harSendtForhåndsvarsel() && brevvalg?.skalSendeBrev() == true) {
                        KunneIkkeLageAvsluttetRevurdering.BrevvalgUtenForhåndsvarsel.left()
                    } else {
                        AvsluttetRevurdering(
                            underliggendeRevurdering,
                            begrunnelse,
                            // Ønsker ikke putte spesifikk domenelogikk inn i [Brevvalg], men vi kunne flyttet denne ut i en enum evt.
                            brevvalg ?: Brevvalg.SkalIkkeSendeBrev("IKKE_FORHÅNDSVARSLET"),
                            tidspunktAvsluttet,
                        ).right()
                    }
                }
            }
        }
    }
}

sealed class KunneIkkeLageAvsluttetRevurdering {
    object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetRevurdering()
    object RevurderingenErIverksatt : KunneIkkeLageAvsluttetRevurdering()
    object RevurderingenErTilAttestering : KunneIkkeLageAvsluttetRevurdering()
    object BrevvalgUtenForhåndsvarsel : KunneIkkeLageAvsluttetRevurdering()
    object ManglerBrevvalgVedForhåndsvarsling : KunneIkkeLageAvsluttetRevurdering()
}

sealed class KunneIkkeAvslutteRevurdering {
    data class KunneIkkeLageAvsluttetRevurdering(val feil: no.nav.su.se.bakover.domain.revurdering.KunneIkkeLageAvsluttetRevurdering) :
        KunneIkkeAvslutteRevurdering()

    data class KunneIkkeLageAvsluttetGjenopptaAvYtelse(val feil: GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse) :
        KunneIkkeAvslutteRevurdering()

    data class KunneIkkeLageAvsluttetStansAvYtelse(val feil: StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse) :
        KunneIkkeAvslutteRevurdering()

    object FantIkkeRevurdering : KunneIkkeAvslutteRevurdering()
    object KunneIkkeLageDokument : KunneIkkeAvslutteRevurdering()
    object FantIkkePersonEllerSaksbehandlerNavn : KunneIkkeAvslutteRevurdering()
    object BrevvalgIkkeTillatt : KunneIkkeAvslutteRevurdering()
}
