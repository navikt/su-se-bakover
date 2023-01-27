package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.SaksbehandlingsHandling
import no.nav.su.se.bakover.domain.behandling.Saksbehandlingshendelse
import no.nav.su.se.bakover.domain.behandling.Saksbehandlingshistorikk

data class Søknadsbehandlingshendelse(
    override val tidspunkt: Tidspunkt,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val handling: SøknadsbehandlingsHandling,
) : Saksbehandlingshendelse

data class Søknadsbehandlingshistorikk private constructor(
    override val historikk: List<Søknadsbehandlingshendelse>,
) : Saksbehandlingshistorikk<Søknadsbehandlingshendelse> {

    override fun leggTilNyHendelse(saksbehandlingsHendelse: Søknadsbehandlingshendelse) =
        Søknadsbehandlingshistorikk(historikk.plus(saksbehandlingsHendelse))

    override fun leggTilNyeHendelser(saksbehandlingsHendelse: NonEmptyList<Søknadsbehandlingshendelse>) =
        Søknadsbehandlingshistorikk(historikk.plus(saksbehandlingsHendelse))

    companion object {
        fun nyHistorikk(hendelse: Søknadsbehandlingshendelse) = Søknadsbehandlingshistorikk(nonEmptyListOf(hendelse))

        fun createFromExisting(historikk: List<Søknadsbehandlingshendelse>) =
            Søknadsbehandlingshistorikk(historikk)
    }
}

sealed interface SøknadsbehandlingsHandling : SaksbehandlingsHandling {
    object StartetBehandling : SøknadsbehandlingsHandling
    object OppdatertStønadsperiode : SøknadsbehandlingsHandling
    object OppdatertUførhet : SøknadsbehandlingsHandling
    object OppdatertOpplysningsplikt : SøknadsbehandlingsHandling
    object OppdatertFlyktning : SøknadsbehandlingsHandling
    object OppdatertLovligOpphold : SøknadsbehandlingsHandling
    object OppdatertInstitusjonsopphold : SøknadsbehandlingsHandling
    object OppdatertUtenlandsopphold : SøknadsbehandlingsHandling
    object OppdatertPersonligOppmøte : SøknadsbehandlingsHandling
    object OppdatertFastOppholdINorge : SøknadsbehandlingsHandling
    object OppdatertFormue : SøknadsbehandlingsHandling

    /**
     * Bruker får ja/nei spørsmål ved Formue-steget i frontend
     */
    object TattStillingTilEPS : SøknadsbehandlingsHandling
    object FullførtBosituasjon : SøknadsbehandlingsHandling
    object OppdatertFradragsgrunnlag : SøknadsbehandlingsHandling
    object Beregnet : SøknadsbehandlingsHandling
    object Simulert : SøknadsbehandlingsHandling
    object SendtTilAttestering : SøknadsbehandlingsHandling
    object Lukket : SøknadsbehandlingsHandling
}
