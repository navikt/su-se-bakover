package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import java.time.Clock
import java.util.UUID

/**
 * Per tidspunkt støtter vi ikke å revurdere Søknadsbehandlinger som førte til Avslag.
 * Når vi kan revurderer Avslag, må man passe på distinksjonen mellom vedtak som fører til endring i ytelsen når man finner gjeldende vedtak og tidslinjer.
 */
sealed interface VedtakSomKanRevurderes : Stønadsvedtak {
    override val id: UUID
    override val opprettet: Tidspunkt
    override val saksbehandler: NavIdentBruker.Saksbehandler
    override val attestant: NavIdentBruker.Attestant
    override val periode: Periode
    override val behandling: Behandling

    fun sakinfo(): SakInfo {
        return behandling.sakinfo()
    }

    companion object {
        fun from(
            søknadsbehandling: IverksattSøknadsbehandling.Innvilget,
            utbetalingId: UUID30,
            clock: Clock,
        ) = VedtakInnvilgetSøknadsbehandling.fromSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            utbetalingId = utbetalingId,
            clock = clock,
        )

        fun from(
            revurdering: IverksattRevurdering.Innvilget,
            utbetalingId: UUID30,
            clock: Clock,
        ) = VedtakInnvilgetRevurdering.from(
            revurdering = revurdering,
            utbetalingId = utbetalingId,
            clock = clock,
        )

        fun from(
            revurdering: IverksattRevurdering.Opphørt,
            utbetalingId: UUID30,
            clock: Clock,
        ) = VedtakOpphørtRevurdering.from(
            revurdering = revurdering,
            utbetalingId = utbetalingId,
            clock = clock,
        )

        fun from(
            revurdering: StansAvYtelseRevurdering.IverksattStansAvYtelse,
            utbetalingId: UUID30,
            clock: Clock,
        ): VedtakStansAvYtelse {
            return VedtakStansAvYtelse.from(
                revurdering = revurdering,
                utbetalingId = utbetalingId,
                clock = clock,
            )
        }

        fun from(
            revurdering: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
            utbetalingId: UUID30,
            clock: Clock,
        ): VedtakGjenopptakAvYtelse {
            return VedtakGjenopptakAvYtelse.from(
                revurdering = revurdering,
                utbetalingId = utbetalingId,
                clock = clock,
            )
        }

        fun from(
            regulering: IverksattRegulering,
            utbetalingId: UUID30,
            clock: Clock,
        ): VedtakInnvilgetRegulering {
            return VedtakInnvilgetRegulering.from(
                regulering = regulering,
                utbetalingId = utbetalingId,
                clock = clock,
            )
        }
    }
}
