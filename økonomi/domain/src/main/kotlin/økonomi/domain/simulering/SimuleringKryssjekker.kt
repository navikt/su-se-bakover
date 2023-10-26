package økonomi.domain.simulering

import arrow.core.Either
import arrow.core.right


fun Simulering.kryssjekk(other: Simulering): Either<UlikeSimuleringer,Unit> {
    if(this.fnr != other.fnr) return Either.Left(UlikeSimuleringer.UlikeFødselsnummer)
    // Det gjør ikke noe om datoBeregnet er forskjellig.
    if(this.nettoBeløp != other.nettoBeløp) return Either.Left(UlikeSimuleringer.UliktNettobeløp)
    if(this.bruttoFeilutbetaling != other.bruttoFeilutbetaling) return Either.Left(UlikeSimuleringer.UlikBruttoFeilutbetaling)
    if(this.bruttoTidligereUtbetalt != other.bruttoTidligereUtbetalt) return Either.Left(UlikeSimuleringer.UlikBruttoTidligereUtbetalt)
    if(this.bruttoTilUtbetaling != other.bruttoTilUtbetaling) return Either.Left(UlikeSimuleringer.UlikBruttoTilUtbetaling)
    if(this.bruttoTotalUtbetaling != other.bruttoTotalUtbetaling) return Either.Left(UlikeSimuleringer.UlikTotalUtbetaling)
    if(this.perioder.size != other.perioder.size) return Either.Left(UlikeSimuleringer.UlikPeriode)
    this.perioder.zip(other.perioder).forEach {
        if(it.first.fraOgMed != it.second.tilOgMed) return Either.Left(UlikeSimuleringer.UlikPeriode)
        if(it.first.fraOgMed != it.second.tilOgMed) return Either.Left(UlikeSimuleringer.UlikPeriode)
        // Forfall kan være interessant, men det er ikke en showstopper.
        if(it.first.bruttoFeilutbetaling != it.second.bruttoFeilutbetaling) return Either.Left(UlikeSimuleringer.UlikBruttoFeilutbetaling)
        if(it.first.bruttoTidligereUtbetalt != it.second.bruttoTidligereUtbetalt) return Either.Left(UlikeSimuleringer.UlikBruttoTidligereUtbetalt)
        if(it.first.bruttoTilUtbetaling != it.second.bruttoTilUtbetaling) return Either.Left(UlikeSimuleringer.UlikBruttoTilUtbetaling)
        if(it.first.bruttoTotalUtbetaling != it.second.bruttoTotalUtbetaling) return Either.Left(UlikeSimuleringer.UlikTotalUtbetaling)
    }
    return Unit.right()
}


sealed interface UlikeSimuleringer {
    data object UlikeFødselsnummer : UlikeSimuleringer
    data object UlikPeriode : UlikeSimuleringer

    /** Her er det snakk om nettobeløpet som Oppdrag har summert for alle periodene (hele simuleringen). */
    data object UliktNettobeløp : UlikeSimuleringer

    // Disse har vi utledet selv.
    data object UlikBruttoFeilutbetaling : UlikeSimuleringer
    data object UlikBruttoTidligereUtbetalt : UlikeSimuleringer
    data object UlikBruttoTilUtbetaling : UlikeSimuleringer
    data object UlikTotalUtbetaling : UlikeSimuleringer
}
