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
    val vurdering: Vurdering,
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
            alderSøkerFyllerIÅr = alderSøkerFyllerIÅr?.let { Year.of(it) },
            alderPåTidspunkt = alderPåTidspunkt,
        )
        val saksbehandlersAvgjørelse = when (saksbehandlerTattEnAvgjørelse) {
            true -> SaksbehandlersAvgjørelse.Avgjort(avgjørelsesTidspunkt!!)
            false -> SaksbehandlersAvgjørelse.TrengerIkkeAvgjørelse
        }
        return when (vurdering) {
            Vurdering.INNVILGET_MED_FØDSELSDATO -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsdato(
                    fødselsdato = LocalDate.parse(fødselsdato),
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            Vurdering.INNVILGET_MED_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår(
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            Vurdering.AVSLAG_MED_FØDSELSDATO -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.RettPåAlder.MedFødselsdato(
                    fødselsdato = LocalDate.parse(fødselsdato),
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            Vurdering.AVSLAG_MED_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår(
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            Vurdering.UKJENT_MED_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.MedFødselsår(
                    fødselsår = Year.of(fødselsår!!),
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            Vurdering.UKJENT_UTEN_FØDSELSÅR -> Aldersvurdering.Vurdert(
                maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår(
                    stønadsperiode = stønadsperiode,
                ),
                saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
                aldersinformasjon = aldersinformasjon,
            )

            Vurdering.HISTORISK -> Aldersvurdering.Historisk(stønadsperiode)
            Vurdering.SKAL_IKKE_VURDERES -> Aldersvurdering.SkalIkkeVurderes(stønadsperiode)
        }
    }

    companion object {
        fun Aldersvurdering.toDBJson(): String {
            return when (this) {
                is Aldersvurdering.Historisk -> AldersvurderingJson(
                    vurdering = Vurdering.HISTORISK,
                    fødselsdato = null,
                    fødselsår = null,
                    alder = null,
                    alderSøkerFyllerIÅr = null,
                    alderPåTidspunkt = null,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                )

                is Aldersvurdering.SkalIkkeVurderes -> AldersvurderingJson(
                    vurdering = Vurdering.SKAL_IKKE_VURDERES,
                    fødselsdato = null,
                    fødselsår = null,
                    alder = null,
                    alderSøkerFyllerIÅr = null,
                    alderPåTidspunkt = null,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                )

                is Aldersvurdering.Vurdert -> AldersvurderingJson(
                    vurdering = Vurdering.HISTORISK,
                    fødselsdato = fødselsdato.toString(),
                    fødselsår = fødselsår?.value,
                    alder = aldersinformasjon.alder,
                    alderSøkerFyllerIÅr = aldersinformasjon.alderSøkerFyllerIÅr?.value,
                    alderPåTidspunkt = aldersinformasjon.alderPåTidspunkt,
                    saksbehandlerTattEnAvgjørelse = when (saksbehandlersAvgjørelse) {
                        is SaksbehandlersAvgjørelse.Avgjort -> true
                        SaksbehandlersAvgjørelse.TrengerIkkeAvgjørelse -> false
                    },
                    avgjørelsesTidspunkt = when (saksbehandlersAvgjørelse) {
                        is SaksbehandlersAvgjørelse.Avgjort -> (saksbehandlersAvgjørelse as SaksbehandlersAvgjørelse.Avgjort).tidspunkt
                        SaksbehandlersAvgjørelse.TrengerIkkeAvgjørelse -> null
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

enum class Vurdering {
    INNVILGET_MED_FØDSELSDATO,
    INNVILGET_MED_FØDSELSÅR,
    AVSLAG_MED_FØDSELSDATO,
    AVSLAG_MED_FØDSELSÅR,
    UKJENT_MED_FØDSELSÅR,
    UKJENT_UTEN_FØDSELSÅR,
    HISTORISK,
    SKAL_IKKE_VURDERES,
}
