package no.nav.su.se.bakover.database.revurdering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ForhåndsvarselDatabaseJson.IngenForhåndsvarsel::class, name = "IngenForhåndsvarsel"),
    JsonSubTypes.Type(value = ForhåndsvarselDatabaseJson.Sendt::class, name = "Sendt"),
    JsonSubTypes.Type(value = ForhåndsvarselDatabaseJson.Besluttet::class, name = "Besluttet"),
)
internal sealed interface ForhåndsvarselDatabaseJson {
    object IngenForhåndsvarsel : ForhåndsvarselDatabaseJson

    object Sendt : ForhåndsvarselDatabaseJson

    data class Besluttet(
        val valg: BeslutningEtterForhåndsvarsling,
        val begrunnelse: String,
    ) : ForhåndsvarselDatabaseJson

    fun toDomain(): Forhåndsvarsel {
        return when (this) {
            is Besluttet -> when (valg) {
                BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger -> Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag(
                    begrunnelse = this.begrunnelse,
                )
                BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger -> Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget(
                    begrunnelse = this.begrunnelse,
                )
                BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer -> Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet(
                    begrunnelse = this.begrunnelse,
                )
            }
            is IngenForhåndsvarsel -> Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles
            is Sendt -> Forhåndsvarsel.UnderBehandling.Sendt
        }
    }

    companion object {
        fun from(forhåndsvarsel: Forhåndsvarsel) =
            when (forhåndsvarsel) {
                is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet -> Besluttet(
                    valg = BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer,
                    begrunnelse = forhåndsvarsel.begrunnelse,
                )
                is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget -> Besluttet(
                    valg = BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger,
                    begrunnelse = forhåndsvarsel.begrunnelse,
                )
                is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag -> Besluttet(
                    valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                    begrunnelse = forhåndsvarsel.begrunnelse,
                )
                Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles -> IngenForhåndsvarsel
                Forhåndsvarsel.UnderBehandling.Sendt -> Sendt
            }
    }
}

internal enum class BeslutningEtterForhåndsvarsling(val beslutning: String) {
    FortsettSammeOpplysninger("FortsettSammeOpplysninger"),
    FortsettMedAndreOpplysninger("FortsettMedAndreOpplysninger"),
    AvsluttUtenEndringer("AvsluttUtenEndringer"),
}
