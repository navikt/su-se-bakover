package no.nav.su.se.bakover.database.søknadsbehandling

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingHandlingDb.Companion.toDb
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk

data class SøknadsbehandlingHendelseJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handling: SøknadsbehandlingHandlingDb,
)

data class SøknadsbehandlingshistorikkJson(
    val historikk: List<SøknadsbehandlingHendelseJson>,
) {

    fun toSøknadsbehandlingsHistorikk() = Søknadsbehandlingshistorikk.createFromExisting(
        this.historikk.map {
            Søknadsbehandlingshendelse(
                tidspunkt = it.tidspunkt,
                saksbehandler = NavIdentBruker.Saksbehandler(it.navIdent),
                handling = it.handling.toSøknadsbehandlingsHandling(),
            )
        },
    )

    companion object {
        fun Søknadsbehandlingshistorikk.toDbJson(): String {
            return SøknadsbehandlingshistorikkJson(
                this.historikk.map {
                    SøknadsbehandlingHendelseJson(
                        tidspunkt = it.tidspunkt,
                        navIdent = it.saksbehandler.navIdent,
                        handling = it.handling.toDb(),
                    )
                },
            ).let { serialize(it) }
        }

        fun toSøknadsbehandlingsHistorikk(json: String): Søknadsbehandlingshistorikk {
            return deserialize<SøknadsbehandlingshistorikkJson>(json).toSøknadsbehandlingsHistorikk()
        }
    }
}

enum class SøknadsbehandlingHandlingDb {
    StartetBehandling,
    OppdatertStønadsperiode,
    OppdatertFastOppholdINorge,
    OppdatertFlyktning,
    OppdatertFormue,
    OppdatertInstitusjonsopphold,
    OppdatertLovligOpphold,
    OppdatertOpplysningsplikt,
    OppdatertPersonligOppmøte,
    OppdatertUførhet,
    OppdatertUtenlandsopphold,
    OppdatertBosituasjon,
    TattStillingTilEPS,
    FullførtBosituasjon,
    OppdatertFradrag,
    Beregnet,
    Simulert,
    SendtTilAttestering,
    Lukket,
    ;

    fun toSøknadsbehandlingsHandling() = when (this) {
        StartetBehandling -> SøknadsbehandlingsHandling.StartetBehandling
        OppdatertStønadsperiode -> SøknadsbehandlingsHandling.OppdatertStønadsperiode
        OppdatertFastOppholdINorge -> SøknadsbehandlingsHandling.OppdatertFastOppholdINorge
        OppdatertFlyktning -> SøknadsbehandlingsHandling.OppdatertFlyktning
        OppdatertFormue -> SøknadsbehandlingsHandling.OppdatertFormue
        OppdatertInstitusjonsopphold -> SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold
        OppdatertLovligOpphold -> SøknadsbehandlingsHandling.OppdatertLovligOpphold
        OppdatertPersonligOppmøte -> SøknadsbehandlingsHandling.OppdatertPersonligOppmøte
        OppdatertUførhet -> SøknadsbehandlingsHandling.OppdatertUførhet
        OppdatertUtenlandsopphold -> SøknadsbehandlingsHandling.OppdatertUtenlandsopphold
        /**
         * Historisk
         * Denne var brukt da søknadsbehandling var delt inn i ufullstendig/fullstendig
         * Videre skal [OppdatertBosituasjon] bli brukt
         */
        TattStillingTilEPS -> SøknadsbehandlingsHandling.TattStillingTilEPS

        /**
         * Historisk
         * Denne var brukt da søknadsbehandling var delt inn i ufullstendig/fullstendig
         * Videre skal [OppdatertBosituasjon] bli brukt
         */
        FullførtBosituasjon -> SøknadsbehandlingsHandling.FullførtBosituasjon
        OppdatertFradrag -> SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag
        Beregnet -> SøknadsbehandlingsHandling.Beregnet
        Simulert -> SøknadsbehandlingsHandling.Simulert
        SendtTilAttestering -> SøknadsbehandlingsHandling.SendtTilAttestering
        Lukket -> SøknadsbehandlingsHandling.Lukket
        OppdatertOpplysningsplikt -> SøknadsbehandlingsHandling.OppdatertOpplysningsplikt
        OppdatertBosituasjon -> SøknadsbehandlingsHandling.OppdatertBosituasjon
    }

    companion object {
        fun SøknadsbehandlingsHandling.toDb() = when (this) {
            SøknadsbehandlingsHandling.Beregnet -> Beregnet
            SøknadsbehandlingsHandling.FullførtBosituasjon -> FullførtBosituasjon
            SøknadsbehandlingsHandling.Lukket -> Lukket
            SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag -> OppdatertFradrag
            SøknadsbehandlingsHandling.OppdatertStønadsperiode -> OppdatertStønadsperiode
            SøknadsbehandlingsHandling.SendtTilAttestering -> SendtTilAttestering
            SøknadsbehandlingsHandling.Simulert -> Simulert
            SøknadsbehandlingsHandling.StartetBehandling -> StartetBehandling
            SøknadsbehandlingsHandling.TattStillingTilEPS -> TattStillingTilEPS
            SøknadsbehandlingsHandling.OppdatertFastOppholdINorge -> OppdatertFastOppholdINorge
            SøknadsbehandlingsHandling.OppdatertFlyktning -> OppdatertFlyktning
            SøknadsbehandlingsHandling.OppdatertFormue -> OppdatertFormue
            SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold -> OppdatertInstitusjonsopphold
            SøknadsbehandlingsHandling.OppdatertLovligOpphold -> OppdatertLovligOpphold
            SøknadsbehandlingsHandling.OppdatertOpplysningsplikt -> OppdatertOpplysningsplikt
            SøknadsbehandlingsHandling.OppdatertPersonligOppmøte -> OppdatertPersonligOppmøte
            SøknadsbehandlingsHandling.OppdatertUførhet -> OppdatertUførhet
            SøknadsbehandlingsHandling.OppdatertUtenlandsopphold -> OppdatertUtenlandsopphold
            SøknadsbehandlingsHandling.OppdatertBosituasjon -> OppdatertBosituasjon
        }
    }
}
