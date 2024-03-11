package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import behandling.domain.SaksbehandlingsHandling
import behandling.domain.Saksbehandlingshendelse
import behandling.domain.Saksbehandlingshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

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
    data object StartetBehandling : SøknadsbehandlingsHandling
    data class StartetBehandlingFraEtAvslag(val tidligereAvslagsId: SøknadsbehandlingId) : SøknadsbehandlingsHandling
    data object OppdatertStønadsperiode : SøknadsbehandlingsHandling
    data object OppdatertUførhet : SøknadsbehandlingsHandling
    data object OppdatertOpplysningsplikt : SøknadsbehandlingsHandling
    data object OppdatertFlyktning : SøknadsbehandlingsHandling
    data object OppdatertLovligOpphold : SøknadsbehandlingsHandling
    data object OppdatertInstitusjonsopphold : SøknadsbehandlingsHandling
    data object OppdatertUtenlandsopphold : SøknadsbehandlingsHandling
    data object OppdatertPersonligOppmøte : SøknadsbehandlingsHandling
    data object OppdatertFastOppholdINorge : SøknadsbehandlingsHandling
    data object OppdatertBosituasjon : SøknadsbehandlingsHandling
    data object OppdatertFormue : SøknadsbehandlingsHandling

    /**
     * Bruker får ja/nei spørsmål ved Formue-steget i frontend
     *
     * Historisk (eller man har benyttet seg av leggTilBosituasjonEpsgrunnlag())
     */
    data object TattStillingTilEPS : SøknadsbehandlingsHandling

    /**
     * Historisk (eller man har benyttet seg av fullførBosituasjongrunnlag())
     */
    data object FullførtBosituasjon : SøknadsbehandlingsHandling

    data object OppdatertFradragsgrunnlag : SøknadsbehandlingsHandling
    data object Beregnet : SøknadsbehandlingsHandling
    data object Simulert : SøknadsbehandlingsHandling
    data object SendtTilAttestering : SøknadsbehandlingsHandling
    data object Lukket : SøknadsbehandlingsHandling

    // Aldersspesifikke handlinger
    data object OppdatertPensjonsvilkår : SøknadsbehandlingsHandling
    data object OppdatertFamiliegjenforening : SøknadsbehandlingsHandling
}
