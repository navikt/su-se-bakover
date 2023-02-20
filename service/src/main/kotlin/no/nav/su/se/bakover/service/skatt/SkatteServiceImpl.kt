package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
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
            .mapLeft { feil -> KunneIkkeHenteSkattemelding.KallFeilet(feil) }
        //.map { hentInntektOgFradrag(it) }
    }
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
