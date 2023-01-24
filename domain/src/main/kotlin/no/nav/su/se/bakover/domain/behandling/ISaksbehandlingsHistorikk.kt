package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår

interface MedSaksbehandlerHistorikk {
    val saksbehandlingsHistorikk: SaksbehandlingsHistorikk
}

private interface ISaksbehandlingsHistorikk {
    val historikk: List<SaksbehandlingsHendelse>

    fun leggTilNyHendelse(saksbehandlingsHendelse: SaksbehandlingsHendelse): List<SaksbehandlingsHendelse>
}

data class SaksbehandlingsHistorikk private constructor(
    override val historikk: List<SaksbehandlingsHendelse>,
) : ISaksbehandlingsHistorikk {

    override fun leggTilNyHendelse(saksbehandlingsHendelse: SaksbehandlingsHendelse): List<SaksbehandlingsHendelse> {
        return historikk.plus(saksbehandlingsHendelse)
    }

    companion object {
        fun nyHistorikk(hendelse: SaksbehandlingsHendelse): SaksbehandlingsHistorikk {
            return SaksbehandlingsHistorikk(listOf(hendelse))
        }

        fun createFromExisting(historikk: SaksbehandlingsHistorikk): SaksbehandlingsHistorikk {
            return SaksbehandlingsHistorikk(historikk.historikk)
        }
    }
}

data class SaksbehandlingsHendelse(
    val tidspunkt: Tidspunkt,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val handling: SaksbehandlingsHandling,
)

data class GrunnlagOgVilkårNavn private constructor(
    val navn: String,
) {
    init {
        require(Navn.checkNavn(navn)) { "$navn ble ikke kjent igjen som et navn på et grunnlag" }
    }

    enum class Navn(val string: String) {
        Bosituasjon("Bosituasjon"),
        Familiegjenforening("Familiegjenforening"),
        Fast_Opphold("Fast opphold"),
        Flyktning("Flyktning"),
        Formue("Formue"),
        Fradrag("Fradrag"),
        Institusjonsopphold("Institusjonsopphold"),
        Lovlig_opphold("Lovlig opphold"),
        Opplysningsplikt("Opplysningsplikt"),
        Pensjon("Pensjon"),
        Personlig_Oppmøte("Personlig oppmøte"),
        Uføre("Uføre"),
        Utenlandsopphold("Utenlandsopphold");

        companion object {
            fun checkNavn(navn: String): Boolean {
                return Either.catch { valueOf(navn) }.isRight()
            }
        }
    }

    companion object {
        fun fromDB(navn: String): GrunnlagOgVilkårNavn {
            return GrunnlagOgVilkårNavn(navn)
        }

        fun Grunnlag.navn(): GrunnlagOgVilkårNavn = when (this) {
            is Grunnlag.Bosituasjon -> GrunnlagOgVilkårNavn("Bosituasjon")
            is FastOppholdINorgeGrunnlag -> GrunnlagOgVilkårNavn("Fast opphold")
            is Formuegrunnlag -> GrunnlagOgVilkårNavn("Formue")
            is Grunnlag.Fradragsgrunnlag -> GrunnlagOgVilkårNavn("Fradrag")
            is LovligOppholdGrunnlag -> GrunnlagOgVilkårNavn("Lovlig opphold")
            is Opplysningspliktgrunnlag -> GrunnlagOgVilkårNavn("Opplysningsplikt")
            is Pensjonsgrunnlag -> GrunnlagOgVilkårNavn("Pensjon")
            is PersonligOppmøteGrunnlag -> GrunnlagOgVilkårNavn("Personlig oppmøte")
            is Grunnlag.Uføregrunnlag -> GrunnlagOgVilkårNavn("Uføre")
            is Utenlandsoppholdgrunnlag -> GrunnlagOgVilkårNavn("Utenlandsopphold")
        }

        fun Vilkår.navn(): GrunnlagOgVilkårNavn = when (this) {
            is FamiliegjenforeningVilkår -> GrunnlagOgVilkårNavn("Familiegjenforening")
            is FastOppholdINorgeVilkår -> GrunnlagOgVilkårNavn("Fast opphold")
            is FlyktningVilkår -> GrunnlagOgVilkårNavn("Flyktning")
            is FormueVilkår -> GrunnlagOgVilkårNavn("Formue")
            is InstitusjonsoppholdVilkår -> GrunnlagOgVilkårNavn("Institusjonsopphold")
            is LovligOppholdVilkår -> GrunnlagOgVilkårNavn("Lovlig opphold")
            is OpplysningspliktVilkår -> GrunnlagOgVilkårNavn("Opplysningsplikt")
            is PensjonsVilkår -> GrunnlagOgVilkårNavn("Pensjon")
            is PersonligOppmøteVilkår -> GrunnlagOgVilkårNavn("Personlig oppmøte")
            is UføreVilkår -> GrunnlagOgVilkårNavn("Uføre")
            is UtenlandsoppholdVilkår -> GrunnlagOgVilkårNavn("Utenlandsopphold")
        }
    }
}


sealed interface SaksbehandlingsHandling{

    sealed interface Søknadsbehandling: SaksbehandlingsHandling {
        object StartetBehandling: Søknadsbehandling
        object OppdatertStønadsperiode: Søknadsbehandling
        data class OppdatertVilkår(val navn: Vilkår): Søknadsbehandling
        data class OppdatertGrunnlag(val navn: Grunnlag): Søknadsbehandling
        object Beregnet: Søknadsbehandling
        object Simulert: Søknadsbehandling
        object SendtTilAttestering: Søknadsbehandling
        object Lukket: Søknadsbehandling
    }

}
