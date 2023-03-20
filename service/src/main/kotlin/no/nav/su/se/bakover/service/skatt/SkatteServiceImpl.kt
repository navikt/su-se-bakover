package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.toRange
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear.Companion.hentMestGyldigeSkattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import java.time.Clock
import java.time.Year
import java.util.UUID

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    val clock: Clock,
) : SkatteService {

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
    ): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag> {
        // TODO jah: Flytt domenelogikken til domenet
        return skatteClient.hentSamletSkattegrunnlag(fnr, Year.now(clock)).fold(
            { KunneIkkeHenteSkattemelding.KallFeilet(it).left() },
            {
                it.hentMestGyldigeSkattegrunnlag()
                    .map { Skattegrunnlag(fnr = fnr, hentetTidspunkt = Tidspunkt.now(clock), årsgrunnlag = it) }
                    .mapLeft { KunneIkkeHenteSkattemelding.KallFeilet(it) }
            },
        )
    }

    override fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        yearRange: YearRange,
    ): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag> {
        return skatteClient.hentSamletSkattegrunnlagForÅrsperiode(fnr, yearRange).fold(
            { KunneIkkeHenteSkattemelding.KallFeilet(it).left() },
            {
                it.hentMestGyldigeSkattegrunnlag()
                    .map { Skattegrunnlag(fnr = fnr, hentetTidspunkt = Tidspunkt.now(clock), årsgrunnlag = it) }
                    .mapLeft { KunneIkkeHenteSkattemelding.KallFeilet(it) }
            },
        )
    }

    override fun hentSamletSkattegrunnlagForBehandling(behandlingId: UUID): Pair<Fnr, Either<KunneIkkeHenteSkattemelding, Skattegrunnlag>> {
        val søknadsbehandling =
            søknadsbehandlingService.hent(SøknadsbehandlingService.HentRequest(behandlingId))
                .getOrElse { throw IllegalStateException("Fant ikke behandling $behandlingId") }

        return søknadsbehandling.fnr to hentSamletSkattegrunnlagForÅr(
            fnr = søknadsbehandling.fnr,
            yearRange = Year.of(
                Math.min(
                    Year.now(clock).minusYears(1).value,
                    søknadsbehandling.stønadsperiode?.periode?.tilOgMed?.year ?: Year.now(clock).value,
                ),
            ).toRange(),
        )
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
