package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettTransactionException
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock

interface ReguleringRunType {
    val sessionFactory: SessionFactory
    val lagreRegulering: (Regulering, TransactionContext) -> Unit
    val lagreVedtak: (Vedtak, TransactionContext) -> Unit
    val klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>
    val notifyObservers: (VedtakInnvilgetRegulering) -> Unit
}

sealed class LiveRun : ReguleringRunType {

    data class Opprettet(
        override val sessionFactory: SessionFactory,
        override val lagreRegulering: (Regulering, TransactionContext) -> Unit,
        override val lagreVedtak: (Vedtak, TransactionContext) -> Unit,
        override val klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        override val notifyObservers: (VedtakInnvilgetRegulering) -> Unit,
    ) : LiveRun() {
        fun kjørSideffekter(regulering: OpprettetRegulering) {
            sessionFactory.withTransactionContext { tx ->
                lagreRegulering(regulering, tx)
            }
        }
    }

    data class Iverksatt(
        override val sessionFactory: SessionFactory,
        override val lagreRegulering: (Regulering, TransactionContext) -> Unit,
        override val lagreVedtak: (Vedtak, TransactionContext) -> Unit,
        override val klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        override val notifyObservers: (VedtakInnvilgetRegulering) -> Unit,
    ) : LiveRun() {
        fun kjørSideffekter(
            regulering: IverksattRegulering,
            utbetaling: Utbetaling.SimulertUtbetaling,
            clock: Clock,
        ) {
            val vedtak: VedtakInnvilgetRegulering = sessionFactory.withTransactionContext { tx ->
                val nyUtbetaling = klargjørUtbetaling(
                    utbetaling,
                    tx,
                ).getOrElse {
                    throw IverksettTransactionException(
                        "Kunne ikke opprette utbetaling. Underliggende feil:$it.",
                        KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(it),
                    )
                }

                val vedtak = VedtakSomKanRevurderes.from(
                    regulering = regulering,
                    utbetalingId = nyUtbetaling.utbetaling.id,
                    clock = clock,
                )

                lagreRegulering(regulering, tx)
                lagreVedtak(vedtak, tx)

                nyUtbetaling.sendUtbetaling()
                    .getOrElse { throw RuntimeException(it.toString()) }

                vedtak
            }
            // Vi ønsker ikke sende statistikken som en del av transaksjonen, siden vi ikke ønsker å rulle tilbake dersom den feiler (best effort).
            notifyObservers(vedtak)
        }
    }
}
