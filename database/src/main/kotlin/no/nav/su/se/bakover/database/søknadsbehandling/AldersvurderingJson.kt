package no.nav.su.se.bakover.database.søknadsbehandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersinformasjon
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.SaksbehandlersAvgjørelse
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import java.time.LocalDate
import java.time.Year

internal class AldersvurderingJson(
    val vurdering: MaskinellVurdering,
    val fødselsdato: String?,
    val fødselsår: Int?,
    val alder: Int?,
    val alderSøkerFyllerIÅr: Int?,
    val alderPåTidspunkt: Tidspunkt?,
    val saksbehandlerTattEnAvgjørelse: Boolean,
    val avgjørelsesTidspunkt: Tidspunkt?,
) {

    fun toAldersvurdering(
        stønadsperiode: Stønadsperiode,
    ): Aldersvurdering {
        val aldersinformasjon = Aldersinformasjon.createFromExisting(
            alder = alder,
            alderSøkerFyllerIÅr = alderSøkerFyllerIÅr,
            alderPåTidspunkt = alderPåTidspunkt,
        )
        val saksbehandlersAvgjørelse = when (saksbehandlerTattEnAvgjørelse) {
            true -> SaksbehandlersAvgjørelse.Avgjort(avgjørelsesTidspunkt!!)
            false -> null
        }
        return when (vurdering) {
            MaskinellVurdering.RETT_MED_FØDSELSDATO -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsdato(
                    fødselsdato = LocalDate.parse(fødselsdato),
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            MaskinellVurdering.RETT_MED_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår(
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            MaskinellVurdering.IKKE_RETT_MED_FØDSELSDATO -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsdato(
                    fødselsdato = LocalDate.parse(fødselsdato),
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            MaskinellVurdering.IKKE_RETT_MED_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsår(
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            MaskinellVurdering.UKJENT_MED_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.MedFødselsår(
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            MaskinellVurdering.UKJENT_UTEN_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår(
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            MaskinellVurdering.HISTORISK -> Aldersvurdering.Historisk(stønadsperiode)
            MaskinellVurdering.SKAL_IKKE_VURDERES -> Aldersvurdering.SkalIkkeVurderes(stønadsperiode)
        }
    }

    companion object {
        fun Aldersvurdering.toDBJson(): String {
            return when (this) {
                is Aldersvurdering.Historisk -> AldersvurderingJson(
                    vurdering = MaskinellVurdering.HISTORISK,
                    fødselsdato = null,
                    fødselsår = null,
                    alder = null,
                    alderSøkerFyllerIÅr = null,
                    alderPåTidspunkt = null,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                )

                is Aldersvurdering.SkalIkkeVurderes -> AldersvurderingJson(
                    vurdering = MaskinellVurdering.SKAL_IKKE_VURDERES,
                    fødselsdato = null,
                    fødselsår = null,
                    alder = null,
                    alderSøkerFyllerIÅr = null,
                    alderPåTidspunkt = null,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                )

                is Aldersvurdering.Vurdert -> AldersvurderingJson(
                    vurdering = when (this.maskinellVurdering) {
                        is MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsdato -> MaskinellVurdering.IKKE_RETT_MED_FØDSELSDATO
                        is MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsår -> MaskinellVurdering.IKKE_RETT_MED_FØDSELSÅR

                        is MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsdato -> MaskinellVurdering.RETT_MED_FØDSELSDATO
                        is MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår -> MaskinellVurdering.RETT_MED_FØDSELSÅR

                        is MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.MedFødselsår -> MaskinellVurdering.UKJENT_MED_FØDSELSÅR
                        is MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår -> MaskinellVurdering.UKJENT_UTEN_FØDSELSÅR
                    },
                    fødselsdato = fødselsdato?.toString(),
                    fødselsår = fødselsår?.value,
                    alder = aldersinformasjon.alder,
                    alderSøkerFyllerIÅr = aldersinformasjon.alderSøkerFyllerIÅr,
                    alderPåTidspunkt = aldersinformasjon.alderPåTidspunkt,
                    saksbehandlerTattEnAvgjørelse = when (saksbehandlersAvgjørelse) {
                        is SaksbehandlersAvgjørelse.Avgjort -> true
                        null -> false
                    },
                    avgjørelsesTidspunkt = when (saksbehandlersAvgjørelse) {
                        is SaksbehandlersAvgjørelse.Avgjort -> (saksbehandlersAvgjørelse as SaksbehandlersAvgjørelse.Avgjort).tidspunkt
                        null -> null
                    },
                )
            }.let { serialize(it) }
        }

        fun toAldersvurdering(
            json: String,
            stønadsperiode: Stønadsperiode,
        ): Aldersvurdering {
            return deserialize<AldersvurderingJson>(json).toAldersvurdering(stønadsperiode)
        }
    }
}

enum class MaskinellVurdering {
    RETT_MED_FØDSELSDATO,
    RETT_MED_FØDSELSÅR,
    IKKE_RETT_MED_FØDSELSDATO,
    IKKE_RETT_MED_FØDSELSÅR,
    UKJENT_MED_FØDSELSÅR,
    UKJENT_UTEN_FØDSELSÅR,
    HISTORISK,
    SKAL_IKKE_VURDERES,
}
