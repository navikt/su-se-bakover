package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeLageAvsluttetGjenopptaAvYtelse
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

sealed class GjenopptaYtelseRevurdering : AbstraktRevurdering() {

    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val simulering: Simulering
    abstract val revurderingsårsak: Revurderingsårsak
    abstract val attesteringer: Attesteringshistorikk

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
        override val tilRevurdering: UUID = underliggendeStansAvYtelse.tilRevurdering
        override val sakinfo: SakInfo = underliggendeStansAvYtelse.sakinfo
        override val id: UUID = underliggendeStansAvYtelse.id
        override val opprettet: Tidspunkt = underliggendeStansAvYtelse.opprettet
        override val periode: Periode = underliggendeStansAvYtelse.periode
        override val grunnlagsdata: Grunnlagsdata = underliggendeStansAvYtelse.grunnlagsdata
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering = underliggendeStansAvYtelse.vilkårsvurderinger
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt.IkkeSendBrev = underliggendeStansAvYtelse.brevvalgRevurdering
        override val saksbehandler: NavIdentBruker.Saksbehandler = underliggendeStansAvYtelse.saksbehandler
        override val simulering: Simulering = underliggendeStansAvYtelse.simulering
        override val revurderingsårsak: Revurderingsårsak = underliggendeStansAvYtelse.revurderingsårsak
        override val attesteringer: Attesteringshistorikk = underliggendeStansAvYtelse.attesteringer

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
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val tilRevurdering: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val simulering: Simulering,
        override val revurderingsårsak: Revurderingsårsak,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt.IkkeSendBrev = BrevvalgRevurdering.Valgt.IkkeSendBrev(
            begrunnelse = null,
            bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
        ),
    ) : GjenopptaYtelseRevurdering() {
        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
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
                sakinfo = sakinfo,
            ).right()
        }
    }

    data class IverksattGjenopptakAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val tilRevurdering: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val simulering: Simulering,
        override val attesteringer: Attesteringshistorikk,
        override val revurderingsårsak: Revurderingsårsak,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt.IkkeSendBrev = BrevvalgRevurdering.Valgt.IkkeSendBrev(
            begrunnelse = null,
            bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
        ),
    ) : GjenopptaYtelseRevurdering(), BehandlingMedAttestering
}
