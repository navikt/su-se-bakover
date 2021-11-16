package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

data class AvsluttetRevurdering private constructor(
    private val underliggendeRevurdering: Revurdering,
    val begrunnelse: String,
    val fritekst: String?,
    val tidspunktAvsluttet: Tidspunkt,
) : Revurdering() {
    override val id: UUID = underliggendeRevurdering.id
    override val opprettet: Tidspunkt = underliggendeRevurdering.opprettet
    override val periode: Periode = underliggendeRevurdering.periode
    override val tilRevurdering: VedtakSomKanRevurderes = underliggendeRevurdering.tilRevurdering
    override val grunnlagsdata: Grunnlagsdata = underliggendeRevurdering.grunnlagsdata
    override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering = underliggendeRevurdering.vilkårsvurderinger
    override val saksbehandler: NavIdentBruker.Saksbehandler = underliggendeRevurdering.saksbehandler
    override val fritekstTilBrev: String = underliggendeRevurdering.fritekstTilBrev
    override val revurderingsårsak: Revurderingsårsak = underliggendeRevurdering.revurderingsårsak
    override val informasjonSomRevurderes: InformasjonSomRevurderes =
        underliggendeRevurdering.informasjonSomRevurderes
    override val forhåndsvarsel: Forhåndsvarsel? = underliggendeRevurdering.forhåndsvarsel
    override val oppgaveId: OppgaveId = underliggendeRevurdering.oppgaveId
    override val attesteringer: Attesteringshistorikk = underliggendeRevurdering.attesteringer

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

    fun skalSendeBrev(): Boolean {
        return this.forhåndsvarsel is Forhåndsvarsel.SkalForhåndsvarsles
    }

    companion object {
        fun tryCreate(
            underliggendeRevurdering: Revurdering,
            begrunnelse: String,
            fritekst: String?,
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
                    if (fritekst != null) {
                        return when (underliggendeRevurdering.forhåndsvarsel) {
                            null,
                            Forhåndsvarsel.IngenForhåndsvarsel,
                            -> KunneIkkeLageAvsluttetRevurdering.FritekstErFylltUtUtenForhåndsvarsel.left()

                            is Forhåndsvarsel.SkalForhåndsvarsles -> AvsluttetRevurdering(
                                underliggendeRevurdering, begrunnelse, fritekst, tidspunktAvsluttet,
                            ).right()
                        }
                    }
                    return AvsluttetRevurdering(
                        underliggendeRevurdering,
                        begrunnelse,
                        fritekst,
                        tidspunktAvsluttet,
                    ).right()
                }
            }
        }
    }
}

sealed class KunneIkkeLageAvsluttetRevurdering {
    object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetRevurdering()
    object RevurderingenErIverksatt : KunneIkkeLageAvsluttetRevurdering()
    object RevurderingenErTilAttestering : KunneIkkeLageAvsluttetRevurdering()
    object FritekstErFylltUtUtenForhåndsvarsel : KunneIkkeLageAvsluttetRevurdering()
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
}
