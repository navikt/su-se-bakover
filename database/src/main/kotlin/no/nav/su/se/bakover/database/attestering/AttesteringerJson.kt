/**
 * Konverter til og fra no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
 */
package no.nav.su.se.bakover.database.attestering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk

internal fun Attesteringshistorikk.toDatabaseJson(): String {
    return this.map {
        when (it) {
            is Attestering.Iverksatt -> AttesteringJson.IverksattJson(
                attestant = it.attestant.navIdent,
                opprettet = it.opprettet,

            )
            is Attestering.Underkjent -> AttesteringJson.UnderkjentJson(
                attestant = it.attestant.navIdent,
                opprettet = it.opprettet,
                grunn = when (it.grunn) {
                    Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT -> AttesteringJson.UnderkjentJson.GrunnJson.INNGANGSVILKÅRENE_ER_FEILVURDERT
                    Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL -> AttesteringJson.UnderkjentJson.GrunnJson.BEREGNINGEN_ER_FEIL
                    Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER -> AttesteringJson.UnderkjentJson.GrunnJson.DOKUMENTASJON_MANGLER
                    Attestering.Underkjent.Grunn.VEDTAKSBREVET_ER_FEIL -> AttesteringJson.UnderkjentJson.GrunnJson.VEDTAKSBREVET_ER_FEIL
                    Attestering.Underkjent.Grunn.ANDRE_FORHOLD -> AttesteringJson.UnderkjentJson.GrunnJson.ANDRE_FORHOLD
                },
                kommentar = it.kommentar,
            )
        }
    }.serialize()
}

internal fun String.toAttesteringshistorikk(): Attesteringshistorikk {
    val attesteringer = deserialize<List<AttesteringJson>>(this)
    return Attesteringshistorikk.create(
        attesteringer.map {
            when (it) {
                is AttesteringJson.IverksattJson -> Attestering.Iverksatt(
                    attestant = NavIdentBruker.Attestant(it.attestant),
                    opprettet = it.opprettet,
                )
                is AttesteringJson.UnderkjentJson -> Attestering.Underkjent(
                    attestant = NavIdentBruker.Attestant(it.attestant),
                    opprettet = it.opprettet,
                    grunn = when (it.grunn) {
                        AttesteringJson.UnderkjentJson.GrunnJson.INNGANGSVILKÅRENE_ER_FEILVURDERT -> Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT
                        AttesteringJson.UnderkjentJson.GrunnJson.BEREGNINGEN_ER_FEIL -> Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL
                        AttesteringJson.UnderkjentJson.GrunnJson.DOKUMENTASJON_MANGLER -> Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER
                        AttesteringJson.UnderkjentJson.GrunnJson.VEDTAKSBREVET_ER_FEIL -> Attestering.Underkjent.Grunn.VEDTAKSBREVET_ER_FEIL
                        AttesteringJson.UnderkjentJson.GrunnJson.ANDRE_FORHOLD -> Attestering.Underkjent.Grunn.ANDRE_FORHOLD
                    },
                    kommentar = it.kommentar,
                )
            }
        },
    )
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AttesteringJson.IverksattJson::class, name = "Iverksatt"),
    JsonSubTypes.Type(value = AttesteringJson.UnderkjentJson::class, name = "Underkjent"),
)
internal sealed class AttesteringJson {
    abstract val attestant: String
    abstract val opprettet: Tidspunkt

    data class IverksattJson(
        override val attestant: String,
        override val opprettet: Tidspunkt,
    ) : AttesteringJson()

    data class UnderkjentJson(
        override val attestant: String,
        override val opprettet: Tidspunkt,
        val grunn: GrunnJson,
        val kommentar: String,
    ) : AttesteringJson() {
        enum class GrunnJson {
            INNGANGSVILKÅRENE_ER_FEILVURDERT,
            BEREGNINGEN_ER_FEIL,
            DOKUMENTASJON_MANGLER,
            VEDTAKSBREVET_ER_FEIL,
            ANDRE_FORHOLD,
        }
    }
}
