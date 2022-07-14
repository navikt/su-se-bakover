package no.nav.su.se.bakover.database.grunnlag

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import java.util.UUID

internal class OpplysningspliktGrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, grunnlag: List<Opplysningspliktgrunnlag>, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreOpplysningspliktgrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            grunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): Opplysningspliktgrunnlag? {
        return dbMetrics.timeQuery("hentOpplysningspliktgrunnlag") {
            """select * from grunnlag_opplysningsplikt where id=:id""".trimIndent()
                .hent(
                    mapOf(
                        "id" to id,
                    ),
                    session,
                ) {
                    it.toOpplysningspliktgrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_opplysningsplikt where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toOpplysningspliktgrunnlag(): Opplysningspliktgrunnlag {
        return Opplysningspliktgrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(
                localDate(columnLabel = "fraOgMed"),
                localDate("tilOgMed"),
            ),
            beskrivelse = string("beskrivelse").beskrivelseToDomain(),
        )
    }

    private fun lagre(grunnlag: Opplysningspliktgrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_opplysningsplikt
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                beskrivelse
            ) values 
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                to_json(:beskrivelse::json)
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to grunnlag.id,
                    "opprettet" to grunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to grunnlag.periode.fraOgMed,
                    "tilOgMed" to grunnlag.periode.tilOgMed,
                    "beskrivelse" to grunnlag.beskrivelse.toDb(),
                ),
                tx,
            )
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = OpplysningspliktBeskrivelseDb.TilstrekkeligDokumentasjon::class,
        name = "TilstrekkeligDokumentasjon",
    ),
    JsonSubTypes.Type(
        value = OpplysningspliktBeskrivelseDb.UtilstrekkeligDokumentasjon::class,
        name = "UtilstrekkeligDokumentasjon",
    ),
)
private sealed class OpplysningspliktBeskrivelseDb {
    object TilstrekkeligDokumentasjon : OpplysningspliktBeskrivelseDb()
    object UtilstrekkeligDokumentasjon : OpplysningspliktBeskrivelseDb()
}

internal fun OpplysningspliktBeskrivelse.toDb(): String {
    return serialize(
        when (this) {
            is OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon -> {
                OpplysningspliktBeskrivelseDb.UtilstrekkeligDokumentasjon
            }
            is OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon -> {
                OpplysningspliktBeskrivelseDb.TilstrekkeligDokumentasjon
            }
        },
    )
}

internal fun String.beskrivelseToDomain(): OpplysningspliktBeskrivelse {
    return when (deserialize<OpplysningspliktBeskrivelseDb>(this)) {
        is OpplysningspliktBeskrivelseDb.UtilstrekkeligDokumentasjon -> {
            OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon
        }
        is OpplysningspliktBeskrivelseDb.TilstrekkeligDokumentasjon -> {
            OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon
        }
    }
}
