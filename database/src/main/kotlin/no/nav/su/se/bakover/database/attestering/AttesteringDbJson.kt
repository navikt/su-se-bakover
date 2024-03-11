package no.nav.su.se.bakover.database.attestering

import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AttesteringDbJson.IverksattJson::class, name = "Iverksatt"),
    JsonSubTypes.Type(value = AttesteringDbJson.UnderkjentJson::class, name = "Underkjent"),
)
/**
 * Per nå, så støtter denne kun underkjente attesteringer som er brukt under søknadsbehandling, revurdering & klage.
 * Dersom klage får egne typer, så må vi potensielt tenke nytt på hvordan lagring at attesteringshistorikken gjøres
 */
sealed interface AttesteringDbJson {
    val attestant: String
    val opprettet: Tidspunkt

    fun toDomain(): Attestering

    data class IverksattJson(
        override val attestant: String,
        override val opprettet: Tidspunkt,
    ) : AttesteringDbJson {
        override fun toDomain(): Attestering.Iverksatt = Attestering.Iverksatt(
            attestant = NavIdentBruker.Attestant(attestant),
            opprettet = opprettet,
        )
    }

    data class UnderkjentJson(
        override val attestant: String,
        override val opprettet: Tidspunkt,
        val grunn: String,
        val kommentar: String,
    ) : AttesteringDbJson {
        override fun toDomain(): Attestering.Underkjent = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(attestant),
            opprettet = opprettet,
            grunn = when (grunn) {
                "INNGANGSVILKÅRENE_ER_FEILVURDERT" -> UnderkjennAttesteringsgrunnBehandling.INNGANGSVILKÅRENE_ER_FEILVURDERT
                "BEREGNINGEN_ER_FEIL" -> UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL
                "DOKUMENTASJON_MANGLER" -> UnderkjennAttesteringsgrunnBehandling.DOKUMENTASJON_MANGLER
                "VEDTAKSBREVET_ER_FEIL" -> UnderkjennAttesteringsgrunnBehandling.VEDTAKSBREVET_ER_FEIL
                "ANDRE_FORHOLD" -> UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD
                else -> throw IllegalStateException("Ukjent grunn - Kunne ikke mappe $grunn til ${UnderkjennAttesteringsgrunnBehandling::class.simpleName}")
            },
            kommentar = kommentar,
        )
    }

    companion object {
        fun Attestering.toDbJson(): AttesteringDbJson = when (this) {
            is Attestering.Iverksatt -> this.toDbJson()
            is Attestering.Underkjent -> this.toDbJson()
        }

        fun Attestering.Iverksatt.toDbJson(): IverksattJson = IverksattJson(
            attestant = attestant.navIdent,
            opprettet = opprettet,
        )

        fun Attestering.Underkjent.toDbJson(): UnderkjentJson = UnderkjentJson(
            attestant = attestant.navIdent,
            opprettet = opprettet,
            grunn = when (grunn) {
                UnderkjennAttesteringsgrunnBehandling.INNGANGSVILKÅRENE_ER_FEILVURDERT -> "INNGANGSVILKÅRENE_ER_FEILVURDERT"
                UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL -> "BEREGNINGEN_ER_FEIL"
                UnderkjennAttesteringsgrunnBehandling.DOKUMENTASJON_MANGLER -> "DOKUMENTASJON_MANGLER"
                UnderkjennAttesteringsgrunnBehandling.VEDTAKSBREVET_ER_FEIL -> "VEDTAKSBREVET_ER_FEIL"
                UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD -> "ANDRE_FORHOLD"
                else -> throw IllegalStateException("Ukjent grunn - kunne ikke mappe $grunn til en database versjon")
            },
            kommentar = kommentar,
        )
    }
}
