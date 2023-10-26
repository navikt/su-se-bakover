package no.nav.su.se.bakover.domain.revurdering.avkorting

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.måneder
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.periode
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import java.util.UUID

/**
 * @return Dersom [Sak.uteståendeAvkorting] er [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes] right, ellers left.
 */
fun Sak.hentUteståendeAvkortingForRevurdering(): Either<AvkortingVedRevurdering.Uhåndtert.IngenUtestående, AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting> {
    return when (uteståendeAvkorting) {
        is Avkortingsvarsel.Ingen -> {
            AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
        }

        is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
            AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
        }

        is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
            AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
        }

        is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
            AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
        }

        is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
            AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(uteståendeAvkorting).right()
        }
    }
}

internal fun kontrollerAtUteståendeAvkortingRevurderes(
    periode: Periode,
    uteståendeAvkorting: AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting,
): Either<KanIkkeRevurderePgaAvkorting.UteståendeAvkortingMåRevurderesISinHelhet, AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting> {
    if (periode.inneholder(uteståendeAvkorting.avkortingsvarsel.periodeUnsafe())) {
        // Vi revurderer hele den utestående avkortingsvarselet. Det er greit.
        return uteståendeAvkorting.right()
    }
    if (periode.overlapper(uteståendeAvkorting.avkortingsvarsel.periodeUnsafe())) {
        // Vi revurderer over deler av et utestående avkortingsvarsel. Det er ikke greit.
        return KanIkkeRevurderePgaAvkorting.UteståendeAvkortingMåRevurderesISinHelhet(uteståendeAvkorting.avkortingsvarsel.periodeUnsafe())
            .left()
    }
    // Vi revurderer på utsiden av avkortingsvarselet. Det er greit.
    return uteståendeAvkorting.right()
}

/**
 * Validerer:
 * - Dersom det finnes en utestående avkorting som overlapper med denne revurderingsperiode, så må vi revurdere avkortingsvarselet i sin helhet.
 * - At vi ikke revurderer et varsel det er pågående avkorting for.
 */
fun Sak.hentOgKontrollerUteståendeAvkorting(
    revurderingsperiode: Periode,
): Either<KanIkkeRevurderePgaAvkorting, AvkortingVedRevurdering.Uhåndtert> {
    // TODO jah: Denne kan heller flyttes til iverksettingssteget og senere attesteringssteget.
    this.unngåRevurderingAvPeriodeDetErPågåendeAvkortingFor(revurderingsperiode)
        .onLeft { return it.left() }

    return hentUteståendeAvkortingForRevurdering().mapLeft {
        // Det finnes ingen utestående avkorting, returnerer bare den direkte.
        return it.right()
    }.flatMap { uteståendeAvkorting ->
        kontrollerAtUteståendeAvkortingRevurderes(
            periode = revurderingsperiode,
            uteståendeAvkorting = uteståendeAvkorting,
        )
    }
}

sealed interface KanIkkeRevurderePgaAvkorting {

    data class UteståendeAvkortingMåRevurderesISinHelhet(
        val periode: Periode,
    ) : KanIkkeRevurderePgaAvkorting

    data class PågåendeAvkortingForPeriode(
        val periode: Periode,
        val vedtakId: UUID,
    ) : KanIkkeRevurderePgaAvkorting
}

/**
 * Unngå at man kan revurdere en periode dersom perioden tidligere har produsert et [no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel]
 * og en stønadsperiode har påbegynt avkortingen av dette. Tillater tilfeller hvor det ikke er overlapp mellom [revurderingsperiode] og avkortingsvarselet, samt tilfeller hvor
 * [revurderingsperiode] dekker både det aktuelle avkortingsvarsel og alle periodene med fradrag for avkorting i den nye stønadsperioden.
 */
fun Sak.unngåRevurderingAvPeriodeDetErPågåendeAvkortingFor(
    revurderingsperiode: Periode,
): Either<KanIkkeRevurderePgaAvkorting.PågåendeAvkortingForPeriode, Unit> {
    val pågåendeAvkorting: List<Pair<VedtakInnvilgetSøknadsbehandling, AvkortingVedSøknadsbehandling.Avkortet>> =
        vedtakstidslinje()
            .let { it ?: throw IllegalStateException("Kunne ikke konstruere vedtakstidslinje for saksnummer $saksnummer siden vi ikke har vedtak.") }
            .asSequence()
            .map { it.originaltVedtak }
            .filterIsInstance<VedtakInnvilgetSøknadsbehandling>()
            .map { it to it.behandling.avkorting }
            .filter { (_, avkorting) -> avkorting is AvkortingVedSøknadsbehandling.Avkortet }
            .filterIsInstance<Pair<VedtakInnvilgetSøknadsbehandling, AvkortingVedSøknadsbehandling.Avkortet>>()
            .toList()

    return if (pågåendeAvkorting.isEmpty()) {
        Unit.right()
    } else {
        pågåendeAvkorting.forEach { (vedtak, pågåendeAvkorting) ->
            if (revurderingsperiode.overlapper(pågåendeAvkorting.avkortingsvarsel.periodeUnsafe())) {
                val periodeSomMåOverlappes = (
                    pågåendeAvkorting.avkortingsvarsel.periodeUnsafe()
                        .måneder() + vedtak.behandling.grunnlagsdata.fradragsgrunnlag.filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                        .periode()
                    ).distinct().måneder()
                if (!revurderingsperiode.inneholder(periodeSomMåOverlappes)) {
                    return KanIkkeRevurderePgaAvkorting.PågåendeAvkortingForPeriode(
                        periode = revurderingsperiode,
                        vedtakId = vedtak.id,
                    ).left()
                }
            }
        }
        Unit.right()
    }
}

/**
 * Dersom en sak har en utestående revurdering, kan vi ikke iverksette en revurdering som fører til en ny periode med avkortingsvarsel.
 */
fun Sak.sjekkForSamtidigeAvkortingsvarsler(
    revurdering: Revurdering,
): Either<KanIkkeOpphørePgaUtenlandsoppholdMedUteståendeAvkortingPåSak, Unit> {
    if (!this.harUteståendeAvkorting()) {
        // Denne begrensningen er ikke aktuell dersom vi ikke har en utestående avkorting på saken.
        return Unit.right()
    }
    // TODO jah: Tar høyde for at AvkortingVedRevurdering skal fjernes. Burde kunne spørre en revurdering om den kommer til å føre til avkorting (kan være flere vilkår).
    if (revurdering.vilkårsvurderinger.utenlandsopphold.erAvslag) {
        return KanIkkeOpphørePgaUtenlandsoppholdMedUteståendeAvkortingPåSak.left()
    }
    return Unit.right()
}

data object KanIkkeOpphørePgaUtenlandsoppholdMedUteståendeAvkortingPåSak

/**
 * Ref dokumentasjon til [Sak], [Sak.uteståendeAvkorting] er enten [Avkortingsvarsel.Ingen] eller [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes]
 */
fun Sak.harUteståendeAvkorting(): Boolean {
    return this.uteståendeAvkorting !is Avkortingsvarsel.Ingen
}
