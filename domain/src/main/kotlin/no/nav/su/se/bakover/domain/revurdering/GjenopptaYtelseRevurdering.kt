package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeLageAvsluttetGjenopptaAvYtelse
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakInfo
import vilkår.uføre.domain.Uføregrunnlag
import økonomi.domain.simulering.Simulering
import java.util.UUID

sealed class GjenopptaYtelseRevurdering : AbstraktRevurdering {

    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val simulering: Simulering
    abstract val revurderingsårsak: Revurderingsårsak
    abstract val attesteringer: Attesteringshistorikk

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
    ): Either<KunneIkkeLageAvsluttetGjenopptaAvYtelse, AvsluttetGjenoppta> {
        return AvsluttetGjenoppta.tryCreate(
            gjenopptakAvYtelseRevurdering = this,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
            avsluttetAv = avsluttetAv,
        )
    }

    data class AvsluttetGjenoppta private constructor(
        private val underliggendeStansAvYtelse: SimulertGjenopptakAvYtelse,
        val begrunnelse: String,
        override val avsluttetTidspunkt: Tidspunkt,
        override val avsluttetAv: NavIdentBruker?,
    ) : GjenopptaYtelseRevurdering(), Avbrutt {
        override val tilRevurdering: UUID = underliggendeStansAvYtelse.tilRevurdering
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis =
            underliggendeStansAvYtelse.vedtakSomRevurderesMånedsvis
        override val sakinfo: SakInfo = underliggendeStansAvYtelse.sakinfo
        override val id: UUID = underliggendeStansAvYtelse.id
        override val opprettet: Tidspunkt = underliggendeStansAvYtelse.opprettet
        override val oppdatert: Tidspunkt = underliggendeStansAvYtelse.oppdatert
        override val periode: Periode = underliggendeStansAvYtelse.periode
        override val grunnlagsdataOgVilkårsvurderinger = underliggendeStansAvYtelse.grunnlagsdataOgVilkårsvurderinger
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt.IkkeSendBrev =
            underliggendeStansAvYtelse.brevvalgRevurdering
        override val saksbehandler: NavIdentBruker.Saksbehandler = underliggendeStansAvYtelse.saksbehandler
        override val simulering: Simulering = underliggendeStansAvYtelse.simulering
        override val revurderingsårsak: Revurderingsårsak = underliggendeStansAvYtelse.revurderingsårsak
        override val attesteringer: Attesteringshistorikk = underliggendeStansAvYtelse.attesteringer
        override val beregning = underliggendeStansAvYtelse.beregning

        /** vi sender ikke noe brev ved stans/gjenoppta */
        fun skalSendeAvslutningsbrev(): Boolean {
            return false
        }

        override fun erÅpen() = false

        companion object {
            fun tryCreate(
                gjenopptakAvYtelseRevurdering: GjenopptaYtelseRevurdering,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
                avsluttetAv: NavIdentBruker?,
            ): Either<KunneIkkeLageAvsluttetGjenopptaAvYtelse, AvsluttetGjenoppta> {
                return when (gjenopptakAvYtelseRevurdering) {
                    is AvsluttetGjenoppta -> KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingErAlleredeAvsluttet.left()
                    is IverksattGjenopptakAvYtelse -> KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingenErIverksatt.left()
                    is SimulertGjenopptakAvYtelse -> AvsluttetGjenoppta(
                        gjenopptakAvYtelseRevurdering,
                        begrunnelse,
                        tidspunktAvsluttet,
                        avsluttetAv,
                    ).right()
                }
            }
        }
    }

    data class SimulertGjenopptakAvYtelse(
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
    ) : GjenopptaYtelseRevurdering() {
        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
        override val beregning = null

        override fun erÅpen() = true

        fun iverksett(attestering: Attestering): Either<KunneIkkeIverksetteGjenopptakAvYtelse, IverksattGjenopptakAvYtelse> {
            if (simulering.harFeilutbetalinger()) {
                return KunneIkkeIverksetteGjenopptakAvYtelse.SimuleringIndikererFeilutbetaling.left()
            }
            return IverksattGjenopptakAvYtelse(
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

    data class IverksattGjenopptakAvYtelse(
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
    ) : GjenopptaYtelseRevurdering(), BehandlingMedAttestering {

        override val beregning = null
        override fun erÅpen() = false

        /**
         * @return null dersom man kaller denne for en alderssak.
         * @throws IllegalStateException Dersom søknadsbehandlingen mangler uføregrunnlag. Dette skal ikke skje. Initen skal også verifisere dette.
         *
         * Se også tilsvarende implementasjon for søknadsbehandling: [no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling.Innvilget.hentUføregrunnlag]
         */
        fun hentUføregrunnlag(): NonEmptyList<Uføregrunnlag>? {
            return when (this.sakstype) {
                Sakstype.ALDER -> null

                Sakstype.UFØRE -> {
                    this.vilkårsvurderinger.uføreVilkår()
                        .getOrElse { throw IllegalStateException("Revurdering uføre: ${this.id} mangler uføregrunnlag") }
                        .grunnlag
                        .toNonEmptyList()
                }
            }
        }
    }
}
