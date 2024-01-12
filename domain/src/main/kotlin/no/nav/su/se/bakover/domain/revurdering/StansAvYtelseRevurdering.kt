package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import økonomi.domain.simulering.Simulering
import java.util.UUID

sealed class StansAvYtelseRevurdering : AbstraktRevurdering {

    abstract val attesteringer: Attesteringshistorikk
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val simulering: Simulering
    abstract val revurderingsårsak: Revurderingsårsak

    /**
     * Stans og gjenoppta er ikke ekte vedtak.
     */
    override fun skalSendeVedtaksbrev(): Boolean {
        return false
    }

    fun avslutt(
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
        avsluttetAv: NavIdentBruker,
    ): Either<KunneIkkeLageAvsluttetStansAvYtelse, AvsluttetStansAvYtelse> {
        return AvsluttetStansAvYtelse.tryCreate(
            stansAvYtelseRevurdering = this,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
            avsluttetAv = avsluttetAv,
        )
    }

    data class AvsluttetStansAvYtelse private constructor(
        private val underliggendeStansAvYtelse: SimulertStansAvYtelse,
        val begrunnelse: String,
        override val avsluttetTidspunkt: Tidspunkt,
        override val avsluttetAv: NavIdentBruker?,
    ) : StansAvYtelseRevurdering(), Avbrutt {
        override val tilRevurdering = underliggendeStansAvYtelse.tilRevurdering
        override val vedtakSomRevurderesMånedsvis = underliggendeStansAvYtelse.vedtakSomRevurderesMånedsvis
        override val sakinfo = underliggendeStansAvYtelse.sakinfo
        override val id = underliggendeStansAvYtelse.id
        override val opprettet = underliggendeStansAvYtelse.opprettet
        override val oppdatert: Tidspunkt = underliggendeStansAvYtelse.oppdatert
        override val periode = underliggendeStansAvYtelse.periode
        override val grunnlagsdataOgVilkårsvurderinger = underliggendeStansAvYtelse.grunnlagsdataOgVilkårsvurderinger
        override val brevvalgRevurdering = underliggendeStansAvYtelse.brevvalgRevurdering
        override val attesteringer = underliggendeStansAvYtelse.attesteringer
        override val saksbehandler = underliggendeStansAvYtelse.saksbehandler
        override val simulering = underliggendeStansAvYtelse.simulering
        override val revurderingsårsak = underliggendeStansAvYtelse.revurderingsårsak

        override val beregning = null

        // vi sender ikke noe brev ved stans/gjenoppta
        fun skalSendeAvslutningsbrev(): Boolean {
            return false
        }

        override fun erÅpen() = false

        companion object {
            fun tryCreate(
                stansAvYtelseRevurdering: StansAvYtelseRevurdering,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
                avsluttetAv: NavIdentBruker?,
            ): Either<KunneIkkeLageAvsluttetStansAvYtelse, AvsluttetStansAvYtelse> {
                return when (stansAvYtelseRevurdering) {
                    is AvsluttetStansAvYtelse -> KunneIkkeLageAvsluttetStansAvYtelse.RevurderingErAlleredeAvsluttet.left()
                    is IverksattStansAvYtelse -> KunneIkkeLageAvsluttetStansAvYtelse.RevurderingenErIverksatt.left()
                    is SimulertStansAvYtelse -> AvsluttetStansAvYtelse(
                        stansAvYtelseRevurdering,
                        begrunnelse,
                        tidspunktAvsluttet,
                        avsluttetAv,
                    ).right()
                }
            }
        }
    }

    data class SimulertStansAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        override val tilRevurdering: UUID,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val simulering: Simulering,
        override val revurderingsårsak: Revurderingsårsak,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt.IkkeSendBrev = BrevvalgRevurdering.Valgt.IkkeSendBrev(
            begrunnelse = null,
            bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
        ),
    ) : StansAvYtelseRevurdering() {

        override val beregning = null

        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()

        override fun erÅpen() = true

        fun iverksett(attestering: Attestering): Either<KunneIkkeIverksetteStansAvYtelse, IverksattStansAvYtelse> {
            if (simulering.harFeilutbetalinger()) {
                return KunneIkkeIverksetteStansAvYtelse.SimuleringIndikererFeilutbetaling.left()
            }
            return IverksattStansAvYtelse(
                id = id,
                opprettet = opprettet,
                oppdatert = oppdatert,
                periode = periode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = saksbehandler,
                simulering = simulering,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(attestering),
                revurderingsårsak = revurderingsårsak,
                sakinfo = sakinfo,
            ).right()
        }
    }

    sealed class KunneIkkeIverksetteStansAvYtelse {
        data object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteStansAvYtelse()
    }

    sealed class KunneIkkeLageAvsluttetStansAvYtelse {
        data object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetStansAvYtelse()
        data object RevurderingenErIverksatt : KunneIkkeLageAvsluttetStansAvYtelse()
    }

    data class IverksattStansAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        override val tilRevurdering: UUID,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val simulering: Simulering,
        override val attesteringer: Attesteringshistorikk,
        override val revurderingsårsak: Revurderingsårsak,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt.IkkeSendBrev = BrevvalgRevurdering.Valgt.IkkeSendBrev(
            begrunnelse = null,
            bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
        ),
    ) : StansAvYtelseRevurdering(), BehandlingMedAttestering {

        override val beregning = null
        override fun erÅpen() = false
    }
}
