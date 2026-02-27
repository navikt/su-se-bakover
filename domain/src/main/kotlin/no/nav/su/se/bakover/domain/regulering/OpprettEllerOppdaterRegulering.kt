package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Clock

private val log = LoggerFactory.getLogger("opprettEllerOppdaterRegulering")

/*
Oppretter en reguleringsbehandling og vurderer om den kan gjennomføres automatisk
TODO bjg mer beskrivelse
*/
fun Sak.opprettReguleringForAutomatiskEllerManuellBehandling(
    fraOgMedMåned: Måned,
    clock: Clock,
    sakerMedRegulerteFradragEksternKilde: SakerMedRegulerteFradragEksternKilde,
    omregningsfaktor: BigDecimal,
): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering, OpprettetRegulering> {
    if (reguleringer.filterIsInstance<ReguleringUnderBehandling>().isNotEmpty()) {
        throw IllegalStateException("Skal ikke kunne finnes åpne reguleringer på dette stadiet. Skal valideres i tidligere steg")
    }

    // TODO bjg heller passere dette som parameter da det gjøres tidligere i flyt
    val gjeldendeVedtaksdata = this.hentGjeldendeVedtaksdataForRegulering(fraOgMedMåned, clock).getOrElse {
        return it.left()
    }

    val regulerteFradragEksternKilde = sakerMedRegulerteFradragEksternKilde.regulerteFradragEksternKilde.singleOrNull {
        it.saksnummer == saksnummer
    } ?: throw IllegalStateException("Sak har feil i fradrag fra ekstern kilde. Sak=$saksnummer")
    return Regulering.opprettRegulering(
        sakId = id,
        saksnummer = saksnummer,
        fnr = fnr,
        gjeldendeVedtaksdata = gjeldendeVedtaksdata,
        clock = clock,
        sakstype = type,
        regulerteFradragEksternKilde = regulerteFradragEksternKilde,
        omregningsfaktor = omregningsfaktor,
    ).mapLeft {
        // TODO AUTO-REG-26 kan dette forbedres?
        Sak.KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes
    }
}

fun Sak.hentGjeldendeVedtaksdataForRegulering(
    fraOgMedMåned: Måned,
    clock: Clock,
): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering, GjeldendeVedtaksdata> {
    val periode = vedtakstidslinje(
        fraOgMed = fraOgMedMåned,
    ).let { tidslinje ->
        (tidslinje ?: emptyList())
            .filterNot { it.erOpphør() }
            .map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
            .minsteAntallSammenhengendePerioder()
            .ifEmpty {
                log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $fraOgMedMåned")
                return Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
            }
    }.also {
        if (it.count() != 1) return Sak.KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig.left()
    }.single()

    return this.hentGjeldendeVedtaksdata(periode = periode, clock = clock).getOrElse { feil ->
        log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere for perioden (${feil.fraOgMed}, ${feil.tilOgMed})")
        return Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
    }.right()
}
