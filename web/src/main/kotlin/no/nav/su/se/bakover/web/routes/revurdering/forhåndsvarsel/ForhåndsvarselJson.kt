package no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ForhåndsvarselJson.IngenForhåndsvarsel::class, name = "INGEN_FORHÅNDSVARSEL"),
    JsonSubTypes.Type(value = ForhåndsvarselJson.SkalVarslesSendt::class, name = "SKAL_FORHÅNDSVARSLES_SENDT"),
    JsonSubTypes.Type(value = ForhåndsvarselJson.SkalVarslesBesluttet::class, name = "SKAL_FORHÅNDSVARSLES_BESLUTTET"),
)
internal sealed class ForhåndsvarselJson {
    object IngenForhåndsvarsel : ForhåndsvarselJson() {
        override fun equals(other: Any?) = other is IngenForhåndsvarsel
    }

    object SkalVarslesSendt : ForhåndsvarselJson() {
        override fun equals(other: Any?) = other is SkalVarslesSendt
    }

    data class SkalVarslesBesluttet(
        val begrunnelse: String,
        val beslutningEtterForhåndsvarsling: BeslutningEtterForhåndsvarsling,
    ) : ForhåndsvarselJson()

    companion object {
        internal fun Forhåndsvarsel.toJson(): ForhåndsvarselJson = when (this) {
            is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet -> SkalVarslesBesluttet(
                begrunnelse = begrunnelse,
                beslutningEtterForhåndsvarsling = BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer,
            )
            is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget -> SkalVarslesBesluttet(
                begrunnelse = begrunnelse,
                beslutningEtterForhåndsvarsling = BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger,
            )
            is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag -> SkalVarslesBesluttet(
                begrunnelse = begrunnelse,
                beslutningEtterForhåndsvarsling = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
            )
            Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles -> IngenForhåndsvarsel
            Forhåndsvarsel.UnderBehandling.Sendt -> SkalVarslesSendt
        }
    }
}

internal enum class BeslutningEtterForhåndsvarsling(val beslutning: String) {
    FortsettSammeOpplysninger("FORTSETT_MED_SAMME_OPPLYSNINGER"),
    FortsettMedAndreOpplysninger("FORTSETT_MED_ANDRE_OPPLYSNINGER"),
    AvsluttUtenEndringer("AVSLUTT_UTEN_ENDRINGER"),
}
