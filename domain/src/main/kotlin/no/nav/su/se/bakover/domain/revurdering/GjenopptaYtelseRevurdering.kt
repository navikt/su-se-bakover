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

sealed class GjenopptaYtelseRevurdering : AbstraktRevurdering() {

    fun avslutt(
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ): Either<KunneIkkeLageAvsluttetGjenopptaAvYtelse, AvsluttetGjenoppta> {
        return AvsluttetGjenoppta.tryCreate(
            gjenopptakAvYtelseRevurdering = this,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        )
    }

    data class AvsluttetGjenoppta private constructor(
        private val underliggendeStansAvYtelse: SimulertGjenopptakAvYtelse,
        val begrunnelse: String,
        val tidspunktAvsluttet: Tidspunkt,
    ) : GjenopptaYtelseRevurdering() {
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
                gjenopptakAvYtelseRevurdering: GjenopptaYtelseRevurdering,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
            ): Either<KunneIkkeLageAvsluttetGjenopptaAvYtelse, AvsluttetGjenoppta> {
                return when (gjenopptakAvYtelseRevurdering) {
                    is AvsluttetGjenoppta -> KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingErAlleredeAvsluttet.left()
                    is IverksattGjenopptakAvYtelse -> KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingenErIverksatt.left()
                    is SimulertGjenopptakAvYtelse -> AvsluttetGjenoppta(
                        gjenopptakAvYtelseRevurdering,
                        begrunnelse,
                        tidspunktAvsluttet,
                    ).right()
                }
            }
        }
    }

    data class SimulertGjenopptakAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre,
        override val tilRevurdering: VedtakSomKanRevurderes,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val simulering: Simulering,
        val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRevurdering() {

        fun iverksett(attestering: Attestering): Either<KunneIkkeIverksetteGjenopptakAvYtelse, IverksattGjenopptakAvYtelse> {
            if (simulering.harFeilutbetalinger()) {
                return KunneIkkeIverksetteGjenopptakAvYtelse.SimuleringIndikererFeilutbetaling.left()
            }
            return IverksattGjenopptakAvYtelse(
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

    sealed class KunneIkkeIverksetteGjenopptakAvYtelse {
        object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteGjenopptakAvYtelse()
    }

    sealed class KunneIkkeLageAvsluttetGjenopptaAvYtelse {
        object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetGjenopptaAvYtelse()
        object RevurderingenErIverksatt : KunneIkkeLageAvsluttetGjenopptaAvYtelse()
    }

    data class IverksattGjenopptakAvYtelse(
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
    ) : GjenopptaYtelseRevurdering(), BehandlingMedAttestering
}
