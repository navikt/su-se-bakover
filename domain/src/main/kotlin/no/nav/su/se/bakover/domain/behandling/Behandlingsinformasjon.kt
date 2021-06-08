package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import java.time.LocalDate
import java.time.Period

data class Behandlingsinformasjon(
    val uførhet: Uførhet? = null,
    val flyktning: Flyktning? = null,
    val lovligOpphold: LovligOpphold? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val institusjonsopphold: Institusjonsopphold? = null,
    val oppholdIUtlandet: OppholdIUtlandet? = null,
    val formue: Formue? = null,
    val personligOppmøte: PersonligOppmøte? = null,
    val bosituasjon: Bosituasjon? = null,
    val ektefelle: EktefellePartnerSamboer? = null,
) {
    private val vilkår = listOf(
        uførhet,
        flyktning,
        lovligOpphold,
        fastOppholdINorge,
        institusjonsopphold,
        oppholdIUtlandet,
        formue,
        personligOppmøte,
    )
    private val allBehandlingsinformasjon: List<Base?>
        get() {
            return vilkår + bosituasjon + ektefelle
        }

    fun patch(
        b: Behandlingsinformasjon
    ) = Behandlingsinformasjon(
        uførhet = b.uførhet ?: this.uførhet,
        flyktning = b.flyktning ?: this.flyktning,
        lovligOpphold = b.lovligOpphold ?: this.lovligOpphold,
        fastOppholdINorge = b.fastOppholdINorge ?: this.fastOppholdINorge,
        institusjonsopphold = b.institusjonsopphold ?: this.institusjonsopphold,
        oppholdIUtlandet = b.oppholdIUtlandet ?: this.oppholdIUtlandet,
        formue = b.formue ?: this.formue,
        personligOppmøte = b.personligOppmøte ?: this.personligOppmøte,
        bosituasjon = b.bosituasjon ?: this.bosituasjon,
        ektefelle = b.ektefelle ?: this.ektefelle,
    )

    fun erInnvilget(): Boolean = allBehandlingsinformasjon.all { it !== null && it.erVilkårOppfylt() }
    fun utledAvslagsgrunner(): List<Avslagsgrunn> = allBehandlingsinformasjon.mapNotNull { it?.avslagsgrunn() }
    fun erAvslag(): Boolean {
        return uførhetOgFlyktningsstatusErVurdertOgMinstEnAvDeErIkkeOppfylt() ||
            (vilkår.all { it !== null } && vilkår.any { it!!.erVilkårIkkeOppfylt() })
    }

    private fun uførhetOgFlyktningsstatusErVurdertOgMinstEnAvDeErIkkeOppfylt(): Boolean {
        if (uførhet != null && flyktning != null) {
            if (uførhet.erVilkårIkkeOppfylt() || flyktning.erVilkårIkkeOppfylt()) {
                return true
            }
        }
        return false
    }

    @JsonIgnore
    fun utledSats(): Either<UfullstendigBehandlingsinformasjon, Sats> {
        return getBeregningStrategy().map { it.sats() }
    }

    @JsonIgnore
    fun getSatsgrunn(): Either<UfullstendigBehandlingsinformasjon, Satsgrunn> {
        return getBeregningStrategy().map { it.satsgrunn() }
    }

    @JsonIgnore
    internal fun getBeregningStrategy(): Either<UfullstendigBehandlingsinformasjon, BeregningStrategy> {
        if (ektefelle == null) return UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
        if (bosituasjon == null) return UfullstendigBehandlingsinformasjon.BosituasjonErUbesvart.left()

        return when (ektefelle) {
            is EktefellePartnerSamboer.Ektefelle -> when {
                ektefelle.er67EllerEldre() -> BeregningStrategy.Eps67EllerEldre
                else -> when (bosituasjon.ektemakeEllerSamboerUførFlyktning) {
                    null -> return UfullstendigBehandlingsinformasjon.EpsUførFlyktningErUbesvart.left()
                    true -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                    false -> BeregningStrategy.EpsUnder67År
                }
            }
            EktefellePartnerSamboer.IngenEktefelle -> when (bosituasjon.delerBolig) {
                null -> return UfullstendigBehandlingsinformasjon.DelerBoligErUbesvart.left()
                true -> BeregningStrategy.BorMedVoksne
                false -> BeregningStrategy.BorAlene
            }
        }.right()
    }

    /** Gjelder for utleding av sats, satsgrunn og beregningsstrategi */
    sealed class UfullstendigBehandlingsinformasjon {
        object BosituasjonErUbesvart : UfullstendigBehandlingsinformasjon()
        object EktefelleErUbesvart : UfullstendigBehandlingsinformasjon()

        /** Dersom man bor med ektefelle kan ikke bosituasjon->ektemakeEllerSamboerUførFlyktning være ubesvart */
        object EpsUførFlyktningErUbesvart : UfullstendigBehandlingsinformasjon()

        /** Når man ikke bor med ektefelle kan ikke bosituasjon->deler_bolig være ubesvart */
        object DelerBoligErUbesvart : UfullstendigBehandlingsinformasjon()
    }

    fun harEktefelle(): Boolean {
        return ektefelle is EktefellePartnerSamboer.Ektefelle
    }

    abstract class Base {
        abstract fun erVilkårOppfylt(): Boolean
        abstract fun erVilkårIkkeOppfylt(): Boolean
        abstract fun avslagsgrunn(): Avslagsgrunn?
    }

    data class Uførhet(
        val status: Status,
        val uføregrad: Int?,
        val forventetInntekt: Int?,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            HarUføresakTilBehandling
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.UFØRHET else null
    }

    data class Flyktning(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.FLYKTNING else null
    }

    data class LovligOpphold(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.OPPHOLDSTILLATELSE else null
    }

    data class FastOppholdINorge(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE else null
    }

    data class Institusjonsopphold(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart,
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON else null
    }

    data class OppholdIUtlandet(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            SkalVæreMerEnn90DagerIUtlandet,
            SkalHoldeSegINorge,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.SkalHoldeSegINorge
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.SkalVæreMerEnn90DagerIUtlandet

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER else null
    }

    data class Formue(
        val status: Status,
        val verdier: Verdier?,
        val epsVerdier: Verdier?,
        val begrunnelse: String?
    ) : Base() {
        data class Verdier(
            val verdiIkkePrimærbolig: Int?,
            val verdiEiendommer: Int?,
            val verdiKjøretøy: Int?,
            val innskudd: Int?,
            val verdipapir: Int?,
            val pengerSkyldt: Int?,
            val kontanter: Int?,
            val depositumskonto: Int?,
        )

        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            MåInnhenteMerInformasjon
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.FORMUE else null
    }

    data class PersonligOppmøte(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            MøttPersonlig,
            IkkeMøttMenVerge,
            IkkeMøttMenSykMedLegeerklæringOgFullmakt,
            IkkeMøttMenKortvarigSykMedLegeerklæring,
            IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
            IkkeMøttPersonlig,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean =
            status.let {
                it == Status.MøttPersonlig ||
                    it == Status.IkkeMøttMenVerge ||
                    it == Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt ||
                    it == Status.IkkeMøttMenKortvarigSykMedLegeerklæring ||
                    it == Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt
            }

        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.IkkeMøttPersonlig

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.PERSONLIG_OPPMØTE else null
    }

    data class Bosituasjon(
        val ektefelle: EktefellePartnerSamboer?,
        val delerBolig: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?
    ) : Base() {
        override fun erVilkårOppfylt(): Boolean {
            val ektefelleEr67EllerEldre = (ektefelle as? EktefellePartnerSamboer.Ektefelle)?.er67EllerEldre()
            if ((ektefelleEr67EllerEldre == false && ektemakeEllerSamboerUførFlyktning == null) && delerBolig == null) {
                return false
            }
            if (ektemakeEllerSamboerUførFlyktning != null && delerBolig != null) {
                throw IllegalStateException("ektemakeEllerSamboerUførFlyktning og delerBolig kan ikke begge være true samtidig")
            }
            return true
        }

        override fun erVilkårIkkeOppfylt(): Boolean = false

        override fun avslagsgrunn(): Avslagsgrunn? = null
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = EktefellePartnerSamboer.Ektefelle::class, name = "Ektefelle"),
        JsonSubTypes.Type(value = EktefellePartnerSamboer.IngenEktefelle::class, name = "IngenEktefelle"),
    )
    sealed class EktefellePartnerSamboer : Base() {
        data class Ektefelle(
            val fnr: Fnr,
            val navn: Person.Navn?,
            val kjønn: String?,
            val fødselsdato: LocalDate?,
            val adressebeskyttelse: String?,
            val skjermet: Boolean?
        ) : EktefellePartnerSamboer() {
            // TODO jah: Hva når fødselsdato er null?
            fun getAlder(): Int? = fødselsdato?.let { Period.between(it, LocalDate.now()).years }

            /**
             * TODO jah: Hva når fødselsdato er null?
             * @throws NullPointerException
             */
            fun er67EllerEldre(): Boolean = getAlder()!! >= 67
        }

        object IngenEktefelle : EktefellePartnerSamboer() {
            override fun equals(other: Any?): Boolean = other is IngenEktefelle
        }

        override fun erVilkårOppfylt(): Boolean = true
        override fun erVilkårIkkeOppfylt(): Boolean = false
        override fun avslagsgrunn(): Avslagsgrunn? = null
    }

    companion object {
        fun lagTomBehandlingsinformasjon() = Behandlingsinformasjon(
            uførhet = null,
            flyktning = null,
            lovligOpphold = null,
            fastOppholdINorge = null,
            institusjonsopphold = null,
            oppholdIUtlandet = null,
            formue = null,
            personligOppmøte = null,
            bosituasjon = null,
            ektefelle = null,
        )
    }

    /**
     * Midlertidig migreringsfunksjon fra Behandlingsinformasjon + Grunnlag.Bosituasjon -> Behandlingsinformasjon
     * Behandlingsinformasjonen ligger blant annet i Vedtaket inntil videre.
     *
     * Dersom fnr har endret seg, fjernes EPS sin formue.
     * @param gjeldendeBosituasjon denne kan være null for søknadsbehandling, men ikke for revurdering.
     * */
    fun oppdaterBosituasjonOgEktefelle(
        gjeldendeBosituasjon: Grunnlag.Bosituasjon?,
        nyBosituasjon: Grunnlag.Bosituasjon,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeHentePerson, Behandlingsinformasjon> {
        val behandlingsinformasjonBosituasjon = when (nyBosituasjon) {
            is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> Bosituasjon(
                ektefelle = hentEktefelle(nyBosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = null,
            )
            is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps -> {
                Bosituasjon(
                    ektefelle = EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = null,
                )
            }
            is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> {
                Bosituasjon(
                    ektefelle = EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = true,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = nyBosituasjon.begrunnelse,
                )
            }
            is Grunnlag.Bosituasjon.Fullstendig.Enslig -> {
                Bosituasjon(
                    ektefelle = EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = false,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = nyBosituasjon.begrunnelse,
                )
            }
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> {
                Bosituasjon(
                    ektefelle = hentEktefelle(nyBosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = nyBosituasjon.begrunnelse,
                )
            }
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> {
                Bosituasjon(
                    ektefelle = hentEktefelle(nyBosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = false,
                    begrunnelse = nyBosituasjon.begrunnelse,
                )
            }
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> {
                Bosituasjon(
                    ektefelle = hentEktefelle(nyBosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = true,
                    begrunnelse = nyBosituasjon.begrunnelse,
                )
            }
        }
        return this.copy(
            bosituasjon = behandlingsinformasjonBosituasjon,
            ektefelle = behandlingsinformasjonBosituasjon.ektefelle,
            formue = fjernEpsFormueHvisEpsHarEndretSeg(gjeldendeBosituasjon, nyBosituasjon),
        ).right()
    }

    private fun fjernEpsFormueHvisEpsHarEndretSeg(
        gjeldendeBosituasjon: Grunnlag.Bosituasjon?,
        nyBosituasjon: Grunnlag.Bosituasjon,
    ): Formue? {
        val gjeldendeEpsFnr: Fnr? =
            (gjeldendeBosituasjon as? Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer)?.fnr
                ?: (gjeldendeBosituasjon as? Grunnlag.Bosituasjon.Ufullstendig.HarEps)?.fnr
        val nyEpsFnr: Fnr? = (nyBosituasjon as? Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer)?.fnr
            ?: (nyBosituasjon as? Grunnlag.Bosituasjon.Ufullstendig.HarEps)?.fnr
        return if (gjeldendeEpsFnr != nyEpsFnr) {
            this.formue?.copy(
                epsVerdier = null,
            )
        } else this.formue
    }

    private fun hentEktefelle(
        fnr: Fnr,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeHentePerson, EktefellePartnerSamboer.Ektefelle> {
        val eps = hentPerson(fnr).getOrHandle {
            return KunneIkkeHentePerson.FantIkkePerson.left()
        }
        return EktefellePartnerSamboer.Ektefelle(
            fnr = fnr,
            navn = eps.navn,
            kjønn = eps.kjønn,
            fødselsdato = eps.fødselsdato,
            adressebeskyttelse = eps.adressebeskyttelse,
            skjermet = eps.skjermet,
        ).right()
    }

    fun harEpsFormue(): Boolean {
        return formue?.epsVerdier != null
    }
}
