package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

sealed class StansAvYtelseRevurdering : AbstraktRevurdering() {

    fun avslutt(
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ): Either<KunneIkkeLageAvsluttetStansAvYtelse, AvsluttetStansAvYtelse> {
        return AvsluttetStansAvYtelse.tryCreate(
            stansAvYtelseRevurdering = this,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        )
    }

    data class AvsluttetStansAvYtelse private constructor(
        private val underliggendeStansAvYtelse: SimulertStansAvYtelse,
        val begrunnelse: String,
        val tidspunktAvsluttet: Tidspunkt,
    ) : StansAvYtelseRevurdering() {
        override val tilRevurdering: VedtakSomKanRevurderes = underliggendeStansAvYtelse.tilRevurdering
        override val id: UUID = underliggendeStansAvYtelse.id
        override val opprettet: Tidspunkt = underliggendeStansAvYtelse.opprettet
        override val periode: Periode = underliggendeStansAvYtelse.periode
        override val grunnlagsdata: Grunnlagsdata = underliggendeStansAvYtelse.grunnlagsdata
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre = underliggendeStansAvYtelse.vilkårsvurderinger
        val saksbehandler: NavIdentBruker.Saksbehandler = underliggendeStansAvYtelse.saksbehandler
        val simulering: Simulering = underliggendeStansAvYtelse.simulering
        val revurderingsårsak: Revurderingsårsak = underliggendeStansAvYtelse.revurderingsårsak

        // vi sender ikke noe brev ved stans/gjenoppta
        fun skalSendeAvslutningsbrev(): Boolean {
            return false
        }

        companion object {
            fun tryCreate(
                stansAvYtelseRevurdering: StansAvYtelseRevurdering,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
            ): Either<KunneIkkeLageAvsluttetStansAvYtelse, AvsluttetStansAvYtelse> {
                return when (stansAvYtelseRevurdering) {
                    is AvsluttetStansAvYtelse -> KunneIkkeLageAvsluttetStansAvYtelse.RevurderingErAlleredeAvsluttet.left()
                    is IverksattStansAvYtelse -> KunneIkkeLageAvsluttetStansAvYtelse.RevurderingenErIverksatt.left()
                    is SimulertStansAvYtelse -> AvsluttetStansAvYtelse(
                        stansAvYtelseRevurdering,
                        begrunnelse,
                        tidspunktAvsluttet,
                    ).right()
                }
            }
        }
    }

    data class SimulertStansAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre,
        override val tilRevurdering: VedtakSomKanRevurderes,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val simulering: Simulering,
        val revurderingsårsak: Revurderingsårsak,
    ) : StansAvYtelseRevurdering() {

        fun iverksett(attestering: Attestering): Either<KunneIkkeIverksetteStansAvYtelse, IverksattStansAvYtelse> {
            if (simulering.harFeilutbetalinger()) {
                return KunneIkkeIverksetteStansAvYtelse.SimuleringIndikererFeilutbetaling.left()
            }
            return IverksattStansAvYtelse(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                simulering = simulering,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(attestering),
                revurderingsårsak = revurderingsårsak,
            ).right()
        }
    }

    sealed class KunneIkkeIverksetteStansAvYtelse {
        object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteStansAvYtelse()
    }

    sealed class KunneIkkeLageAvsluttetStansAvYtelse {
        object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetStansAvYtelse()
        object RevurderingenErIverksatt : KunneIkkeLageAvsluttetStansAvYtelse()
    }

    data class IverksattStansAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre,
        override val tilRevurdering: VedtakSomKanRevurderes,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val simulering: Simulering,
        override val attesteringer: Attesteringshistorikk,
        val revurderingsårsak: Revurderingsårsak,
    ) : StansAvYtelseRevurdering(), BehandlingMedAttestering
}
