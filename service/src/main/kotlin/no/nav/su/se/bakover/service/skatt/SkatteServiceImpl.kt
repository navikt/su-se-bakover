package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.client.skatteetaten.SkatteoppslagFeil
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Stadie
import java.time.Clock
import java.time.Year

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    val clock: Clock,
) : SkatteService {

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
    ): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag> {
        // TODO jah: Flytt domenelogikken til domenet
        return skatteClient.hentSamletSkattegrunnlag(fnr, Year.now(clock))
            .hentMestGyldigeSkattegrunnlag()
            .mapLeft { it }
    }
}


fun List<Pair<Either<SkatteoppslagFeil, Skattegrunnlag>, Stadie>>.hentMestGyldigeSkattegrunnlag(): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag> {
    this.single { it.second == Stadie.FASTSATT }.let {
        it.first.mapLeft {
            when (it.mapTilOmFeilKanSkippesEllerReturneres()) {
                SkatteoppslagFeilMediator.KanSkippes -> Unit
                SkatteoppslagFeilMediator.SkalReturneres -> return KunneIkkeHenteSkattemelding.KallFeilet(it).left()
            }
        }.map {
            return it.right()
        }
    }
    this.single { it.second == Stadie.OPPGJØR }.let {
        it.first.mapLeft {
            when (it.mapTilOmFeilKanSkippesEllerReturneres()) {
                SkatteoppslagFeilMediator.KanSkippes -> Unit
                SkatteoppslagFeilMediator.SkalReturneres -> return  KunneIkkeHenteSkattemelding.KallFeilet(it).left()
            }
        }.map {
            return it.right()
        }
    }
    this.single { it.second == Stadie.UTKAST }.let {
        it.first.mapLeft {
            when (it.mapTilOmFeilKanSkippesEllerReturneres()) {
                SkatteoppslagFeilMediator.KanSkippes -> Unit
                SkatteoppslagFeilMediator.SkalReturneres -> return KunneIkkeHenteSkattemelding.KallFeilet(it).left()
            }
        }.map {
            return it.right()
        }
    }

    return  KunneIkkeHenteSkattemelding.KallFeilet(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr).left()
}

private fun SkatteoppslagFeil.mapTilOmFeilKanSkippesEllerReturneres(): SkatteoppslagFeilMediator {
    return when (this) {
        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr -> SkatteoppslagFeilMediator.KanSkippes
        SkatteoppslagFeil.ManglerRettigheter -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.Nettverksfeil -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.UkjentFeil -> SkatteoppslagFeilMediator.SkalReturneres
    }
}

sealed interface SkatteoppslagFeilMediator {
    object KanSkippes : SkatteoppslagFeilMediator
    object SkalReturneres : SkatteoppslagFeilMediator
}


/* TODO jah: Finn ut om vi skal filtrere bort ting på dette nivået.
private fun hentInntektOgFradrag(skattegrunnlag: Skattegrunnlag): Skattegrunnlag {
    return skattegrunnlag.copy(
        grunnlag = skattegrunnlag.grunnlag.filter {
            it.spesifisering.contains(Skattegrunnlag.Spesifisering.INNTEKT) ||
                it.spesifisering.contains(Skattegrunnlag.Spesifisering.FORMUE)
        },
    )
}*/
