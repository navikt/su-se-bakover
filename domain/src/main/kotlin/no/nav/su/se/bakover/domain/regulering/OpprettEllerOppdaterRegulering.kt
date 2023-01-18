package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.Sak
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

private val log = LoggerFactory.getLogger("opprettEllerOppdaterRegulering")

/**
 * Iverksatte regulering vil ikke bli oppdatert
 *
 * @return Dersom Either.Left: Disse skal det ikke lages noen regulering for. Denne funksjonen har logget.
 */
fun Sak.opprettEllerOppdaterRegulering(
    // TODO jah: Bytt til YearMonth (Da slipper vi en unødvendig left)
    startDato: LocalDate,
    clock: Clock,
): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering, Regulering.OpprettetRegulering> {
    val (reguleringsId, opprettet, _startDato) = reguleringer.filterIsInstance<Regulering.OpprettetRegulering>()
        .let { r ->
            when (r.size) {
                0 -> Triple(UUID.randomUUID(), Tidspunkt.now(clock), startDato).also {
                    if (harÅpenBehandling()) {
                        return Sak.KunneIkkeOppretteEllerOppdatereRegulering.HarÅpenBehandling.left()
                    }
                }

                1 -> Triple(r.first().id, r.first().opprettet, minOf(startDato, r.first().periode.fraOgMed)).also {
                    if (harÅpenSøknadsbehandling() || harÅpenRevurdering()) {
                        return Sak.KunneIkkeOppretteEllerOppdatereRegulering.HarÅpenBehandling.left()
                    }
                }

                else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende grunn: Det finnes fler enn en åpen regulering.")
            }
        }

    val periode = vedtakstidslinje(
        periode = Periode.create(
            fraOgMed = _startDato,
            tilOgMed = LocalDate.MAX,
        ),
    ).tidslinje.let { tidslinje ->
        tidslinje.filterNot { it.erOpphør() }.map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
            .minsteAntallSammenhengendePerioder().ifEmpty {
                log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $_startDato")
                return Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
            }
    }.also {
        if (it.count() != 1) return Sak.KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig.left()
    }.single()

    val gjeldendeVedtaksdata = this.hentGjeldendeVedtaksdata(periode = periode, clock = clock).getOrElse { feil ->
        log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere for perioden (${feil.fraOgMed}, ${feil.tilOgMed})")
        return Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
    }

    return Regulering.opprettRegulering(
        id = reguleringsId,
        opprettet = opprettet,
        sakId = id,
        saksnummer = saksnummer,
        fnr = fnr,
        gjeldendeVedtaksdata = gjeldendeVedtaksdata,
        clock = clock,
        sakstype = type,
    ).mapLeft {
        Sak.KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes
    }
}
