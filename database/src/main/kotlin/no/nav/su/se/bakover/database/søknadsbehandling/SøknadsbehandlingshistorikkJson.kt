package no.nav.su.se.bakover.database.søknadsbehandling

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingHandlingDb.Companion.toDb
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import java.util.UUID

internal data class SøknadsbehandlingHendelseJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handlingJson: HandlingJson,
)

data class HandlingJson(
    val handling: SøknadsbehandlingHandlingDb,
    val tilhørendeSøknadsbehandlingId: String? = null,
) {
    fun toSøknadsbehandlingsHandling() =
        handling.toSøknadsbehandlingsHandling(tilhørendeSøknadsbehandlingId?.let { SøknadsbehandlingId(UUID.fromString(it)) })
}

internal data class SøknadsbehandlingshistorikkJson(
    val historikk: List<SøknadsbehandlingHendelseJson>,
) {

    fun toSøknadsbehandlingsHistorikk() = Søknadsbehandlingshistorikk.createFromExisting(
        this.historikk.map {
            Søknadsbehandlingshendelse(
                tidspunkt = it.tidspunkt,
                saksbehandler = NavIdentBruker.Saksbehandler(it.navIdent),
                handling = it.handlingJson.toSøknadsbehandlingsHandling(),
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
                        handlingJson = it.handling.toDb(),
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
    StartetFraEtAvslag,

    // Unike for alder
    OppdatertPensjonsvilkår,
    OppdatertFamiliegjenforening,
    ;

    fun toSøknadsbehandlingsHandling(tidligereAvslagsId: SøknadsbehandlingId? = null) = when (this) {
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
        OppdatertPensjonsvilkår -> SøknadsbehandlingsHandling.OppdatertPensjonsvilkår
        OppdatertFamiliegjenforening -> SøknadsbehandlingsHandling.OppdatertFamiliegjenforening
        StartetFraEtAvslag -> SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(tidligereAvslagsId!!)
    }

    companion object {
        fun SøknadsbehandlingsHandling.toDb(): HandlingJson = when (this) {
            SøknadsbehandlingsHandling.Beregnet -> HandlingJson(Beregnet)
            SøknadsbehandlingsHandling.FullførtBosituasjon -> HandlingJson(FullførtBosituasjon)
            SøknadsbehandlingsHandling.Lukket -> HandlingJson(Lukket)
            SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag -> HandlingJson(OppdatertFradrag)
            SøknadsbehandlingsHandling.OppdatertStønadsperiode -> HandlingJson(OppdatertStønadsperiode)
            SøknadsbehandlingsHandling.SendtTilAttestering -> HandlingJson(SendtTilAttestering)
            SøknadsbehandlingsHandling.Simulert -> HandlingJson(Simulert)
            SøknadsbehandlingsHandling.StartetBehandling -> HandlingJson(StartetBehandling)
            SøknadsbehandlingsHandling.TattStillingTilEPS -> HandlingJson(TattStillingTilEPS)
            SøknadsbehandlingsHandling.OppdatertFastOppholdINorge -> HandlingJson(OppdatertFastOppholdINorge)
            SøknadsbehandlingsHandling.OppdatertFlyktning -> HandlingJson(OppdatertFlyktning)
            SøknadsbehandlingsHandling.OppdatertFormue -> HandlingJson(OppdatertFormue)
            SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold -> HandlingJson(OppdatertInstitusjonsopphold)
            SøknadsbehandlingsHandling.OppdatertLovligOpphold -> HandlingJson(OppdatertLovligOpphold)
            SøknadsbehandlingsHandling.OppdatertOpplysningsplikt -> HandlingJson(OppdatertOpplysningsplikt)
            SøknadsbehandlingsHandling.OppdatertPersonligOppmøte -> HandlingJson(OppdatertPersonligOppmøte)
            SøknadsbehandlingsHandling.OppdatertUførhet -> HandlingJson(OppdatertUførhet)
            SøknadsbehandlingsHandling.OppdatertUtenlandsopphold -> HandlingJson(OppdatertUtenlandsopphold)
            SøknadsbehandlingsHandling.OppdatertBosituasjon -> HandlingJson(OppdatertBosituasjon)
            SøknadsbehandlingsHandling.OppdatertFamiliegjenforening -> HandlingJson(OppdatertFamiliegjenforening)
            SøknadsbehandlingsHandling.OppdatertPensjonsvilkår -> HandlingJson(OppdatertPensjonsvilkår)
            is SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag -> HandlingJson(
                StartetFraEtAvslag,
                this.tidligereAvslagsId.toString(),
            )
        }
    }
}
