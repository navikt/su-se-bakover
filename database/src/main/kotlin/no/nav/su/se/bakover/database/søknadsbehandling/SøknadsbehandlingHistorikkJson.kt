package no.nav.su.se.bakover.database.søknadsbehandling

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.søknadsbehandling.InngangsVilkårDb.Companion.toDb
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingHandlingDb.Companion.toDb
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår

data class SøknadsbehandlingHendelseJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handling: SøknadsbehandlingHandlingDb,
    val vilkår: InngangsVilkårDb?,
)

data class SøknadsbehandlingHistorikkJson(
    val historikk: List<SøknadsbehandlingHendelseJson>,
) {

    fun toSøknadsbehandlingsHistorikk() = Søknadsbehandlingshistorikk.createFromExisting(
        this.historikk.map {
            Søknadsbehandlingshendelse(
                tidspunkt = it.tidspunkt,
                saksbehandler = NavIdentBruker.Saksbehandler(it.navIdent),
                handling = it.handling.toSøknadsbehandlingHandling(it.vilkår),
            )
        },
    )

    companion object {
        fun Søknadsbehandlingshistorikk.toDbJson(): String {
            return SøknadsbehandlingHistorikkJson(
                this.historikk.map {
                    SøknadsbehandlingHendelseJson(
                        tidspunkt = it.tidspunkt,
                        navIdent = it.saksbehandler.navIdent,
                        handling = it.handling.toDb(),
                        vilkår = when (it.handling) {
                            is SøknadsbehandlingsHandling.OppdatertVilkår -> (it.handling as SøknadsbehandlingsHandling.OppdatertVilkår).inngangsvilkår.toDb()
                            else -> null
                        },
                    )
                },
            ).let { serialize(it) }
        }

        fun toSøknadsbehandlingsHistorikk(json: String): Søknadsbehandlingshistorikk {
            return deserialize<SøknadsbehandlingHistorikkJson>(json).toSøknadsbehandlingsHistorikk()
        }
    }
}

enum class SøknadsbehandlingHandlingDb {
    StartetBehandling,
    OppdatertStønadsperiode,
    OppdatertVilkår,
    TattStillingTilEPS,
    OppdatertFormue,
    FullførBosituasjon,
    OppdatertFradrag,
    Beregnet,
    Simulert,
    SendtTilAttestering,
    Lukket,
    ;

    fun toSøknadsbehandlingHandling(inngangsvilkår: InngangsVilkårDb?): SøknadsbehandlingsHandling {
        return when (this) {
            StartetBehandling -> SøknadsbehandlingsHandling.StartetBehandling
            OppdatertStønadsperiode -> SøknadsbehandlingsHandling.OppdatertStønadsperiode
            OppdatertVilkår -> SøknadsbehandlingsHandling.OppdatertVilkår(inngangsvilkår!!.toInngangsVilkår())
            TattStillingTilEPS -> SøknadsbehandlingsHandling.TattStillingTilEPS
            OppdatertFormue -> SøknadsbehandlingsHandling.OppdatertFormue
            FullførBosituasjon -> SøknadsbehandlingsHandling.FullførBosituasjon
            OppdatertFradrag -> SøknadsbehandlingsHandling.OppdatertFradrag
            Beregnet -> SøknadsbehandlingsHandling.Beregnet
            Simulert -> SøknadsbehandlingsHandling.Simulert
            SendtTilAttestering -> SøknadsbehandlingsHandling.SendtTilAttestering
            Lukket -> SøknadsbehandlingsHandling.Lukket
        }
    }

    companion object {
        fun SøknadsbehandlingsHandling.toDb() = when (this) {
            SøknadsbehandlingsHandling.Beregnet -> Beregnet
            SøknadsbehandlingsHandling.FullførBosituasjon -> FullførBosituasjon
            SøknadsbehandlingsHandling.Lukket -> Lukket
            SøknadsbehandlingsHandling.OppdatertFormue -> OppdatertFormue
            SøknadsbehandlingsHandling.OppdatertFradrag -> OppdatertFradrag
            SøknadsbehandlingsHandling.OppdatertStønadsperiode -> OppdatertStønadsperiode
            is SøknadsbehandlingsHandling.OppdatertVilkår -> OppdatertVilkår
            SøknadsbehandlingsHandling.SendtTilAttestering -> SendtTilAttestering
            SøknadsbehandlingsHandling.Simulert -> Simulert
            SøknadsbehandlingsHandling.StartetBehandling -> StartetBehandling
            SøknadsbehandlingsHandling.TattStillingTilEPS -> TattStillingTilEPS
        }
    }
}

enum class InngangsVilkårDb {
    Uførhet,
    Formue,
    Flyktning,
    LovligOpphold,
    Institusjonsopphold,
    Utenlandsopphold,
    PersonligOppmøte,
    FastOppholdINorge,
    Opplysningsplikt,
    Familiegjenforening,
    Pensjon,
    ;

    fun toInngangsVilkår() = when (this) {
        Uførhet -> Inngangsvilkår.Uførhet
        Formue -> Inngangsvilkår.Formue
        Flyktning -> Inngangsvilkår.Flyktning
        LovligOpphold -> Inngangsvilkår.LovligOpphold
        Institusjonsopphold -> Inngangsvilkår.Institusjonsopphold
        Utenlandsopphold -> Inngangsvilkår.Utenlandsopphold
        PersonligOppmøte -> Inngangsvilkår.PersonligOppmøte
        FastOppholdINorge -> Inngangsvilkår.FastOppholdINorge
        Opplysningsplikt -> Inngangsvilkår.Opplysningsplikt
        Familiegjenforening -> Inngangsvilkår.Familiegjenforening
        Pensjon -> Inngangsvilkår.Pensjon
    }

    companion object {
        fun Inngangsvilkår.toDb() = when (this) {
            Inngangsvilkår.Familiegjenforening -> Familiegjenforening
            Inngangsvilkår.FastOppholdINorge -> FastOppholdINorge
            Inngangsvilkår.Flyktning -> Flyktning
            Inngangsvilkår.Formue -> Formue
            Inngangsvilkår.Institusjonsopphold -> Institusjonsopphold
            Inngangsvilkår.LovligOpphold -> LovligOpphold
            Inngangsvilkår.Opplysningsplikt -> Opplysningsplikt
            Inngangsvilkår.Pensjon -> Pensjon
            Inngangsvilkår.PersonligOppmøte -> PersonligOppmøte
            Inngangsvilkår.Uførhet -> Uførhet
            Inngangsvilkår.Utenlandsopphold -> Utenlandsopphold
        }
    }
}
