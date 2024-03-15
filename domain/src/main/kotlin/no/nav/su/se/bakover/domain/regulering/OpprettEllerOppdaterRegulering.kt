package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock

private val log = LoggerFactory.getLogger("opprettEllerOppdaterRegulering")

/**
 * Iverksatte regulering vil ikke bli oppdatert
 *
 * @return Dersom Either.Left: Disse skal det ikke lages noen regulering for. Denne funksjonen har logget.
 */
fun Sak.opprettEllerOppdaterRegulering(
    fraOgMedMåned: Måned,
    clock: Clock,
    ignoredFradrag: List<Fradragstype>,
): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering, OpprettetRegulering> {
    val (reguleringsId, opprettet, _fraOgMedMåned) = reguleringer.filterIsInstance<OpprettetRegulering>()
        .let { r ->
            when (r.size) {
                0 -> Triple(ReguleringId.generer(), Tidspunkt.now(clock), fraOgMedMåned)

                1 -> Triple(r.first().id, r.first().opprettet, minOf(fraOgMedMåned, Måned.fra(r.first().periode.fraOgMed)))

                else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende grunn: Det finnes fler enn en åpen regulering.")
            }
        }

    val periode = vedtakstidslinje(
        fraOgMed = _fraOgMedMåned,
    ).let { tidslinje ->
        (tidslinje ?: emptyList())
            .filterNot { it.erOpphør() }
            .map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
            .minsteAntallSammenhengendePerioder()
            .ifEmpty {
                log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $_fraOgMedMåned")
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
        ignoredFradrag = ignoredFradrag,
    ).mapLeft {
        Sak.KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes
    }
}
