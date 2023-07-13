package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
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
    data object StartetBehandling : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertStønadsperiode : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertUførhet : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertOpplysningsplikt : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertFlyktning : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertLovligOpphold : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertInstitusjonsopphold : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertUtenlandsopphold : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertPersonligOppmøte : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertFastOppholdINorge : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertBosituasjon : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object OppdatertFormue : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }

    /**
     * Bruker får ja/nei spørsmål ved Formue-steget i frontend
     *
     * Historisk (eller man har benyttet seg av leggTilBosituasjonEpsgrunnlag())
     */
    data object TattStillingTilEPS : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }

    /**
     * Historisk (eller man har benyttet seg av fullførBosituasjongrunnlag())
     */
    data object FullførtBosituasjon : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }

    data object OppdatertFradragsgrunnlag : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object Beregnet : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object Simulert : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object SendtTilAttestering : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
    data object Lukket : SøknadsbehandlingsHandling {
        override fun toString(): String = this.javaClass.simpleName
    }
}
