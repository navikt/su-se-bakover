package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.skatteetaten.SamletSkattegrunnlagResponseMedStadie.Companion.hentMestGyldigeSkattegrunnlag
import no.nav.su.se.bakover.client.skatteetaten.SamletSkattegrunnlagResponseMedYear
import no.nav.su.se.bakover.client.skatteetaten.SamletSkattegrunnlagResponseMedYear.Companion.hentMestGyldigeSkattegrunnlag
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.Clock
import java.time.Year
import java.util.concurrent.ConcurrentLinkedQueue

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
            .mapLeft { KunneIkkeHenteSkattemelding.KallFeilet(it) }
    }

    override fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        yearRange: YearRange,
    ): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag> {
        val response = ConcurrentLinkedQueue<SamletSkattegrunnlagResponseMedYear>()
        runBlocking {
            yearRange.forEach {
                launch(Dispatchers.IO) {
                    response.add(
                        SamletSkattegrunnlagResponseMedYear(skatteClient.hentSamletSkattegrunnlag(fnr, it), it),
                    )
                }
            }
        }
        return response.hentMestGyldigeSkattegrunnlag().mapLeft {
            KunneIkkeHenteSkattemelding.KallFeilet(it)
        }
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
