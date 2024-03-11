package no.nav.su.se.bakover.domain.revurdering

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingsbehandlingUnderRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vilkår.uføreVilkår
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.uføre.domain.Uføregrunnlag
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed interface IverksattRevurdering : Revurdering {
    abstract override val id: RevurderingId
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: UUID
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract override val revurderingsårsak: Revurderingsårsak
    abstract override val beregning: Beregning
    val attestering: Attestering
        get() = attesteringer.hentSisteAttestering()
    val tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet
    abstract override val brevvalgRevurdering: BrevvalgRevurdering.Valgt

    fun avventerKravgrunnlag(): Boolean {
        return tilbakekrevingsbehandling.avventerKravgrunnlag()
    }

    override fun skalTilbakekreve() = tilbakekrevingsbehandling.skalTilbakekreve().isRight()

    override fun skalSendeVedtaksbrev() = brevvalgRevurdering.skalSendeBrev().isRight()

    override fun erÅpen() = false

    data class Innvilget(
        override val id: RevurderingId,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt,
    ) : IverksattRevurdering {

        override val erOpphørt = false

        override fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> = emptyList()

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

    data class Opphørt(
        override val id: RevurderingId,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val attesteringer: Attesteringshistorikk,
        override val tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt,
    ) : IverksattRevurdering {

        override val erOpphørt = true

        override fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger = vilkårsvurderinger,
                beregning = beregning,
                clock = clock,
            ).resultat
            return when (opphør) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        fun utledOpphørsdato(clock: Clock): LocalDate? {
            val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger = vilkårsvurderinger,
                beregning = beregning,
                clock = clock,
            ).resultat
            return when (opphør) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsdato
                OpphørVedRevurdering.Nei -> null
            }
        }
    }
}

fun VedtakSomKanRevurderes.Companion.fromOpphør(
    revurdering: IverksattRevurdering.Opphørt,
    utbetalingId: UUID30,
    clock: Clock,
) = VedtakOpphørMedUtbetaling.from(
    revurdering = revurdering,
    utbetalingId = utbetalingId,
    clock = clock,
)

fun VedtakSomKanRevurderes.Companion.fromRevurderingInnvilget(
    revurdering: IverksattRevurdering.Innvilget,
    utbetalingId: UUID30,
    clock: Clock,
) = VedtakInnvilgetRevurdering.from(
    revurdering = revurdering,
    utbetalingId = utbetalingId,
    clock = clock,
)
