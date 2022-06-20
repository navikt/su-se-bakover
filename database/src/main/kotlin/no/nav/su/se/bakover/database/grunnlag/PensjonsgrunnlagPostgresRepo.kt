package no.nav.su.se.bakover.database.grunnlag

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsopplysninger
import java.util.UUID

internal class PensjonsgrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, grunnlag: List<Pensjonsgrunnlag>, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagrePensjonsgrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            grunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): Pensjonsgrunnlag? {
        return dbMetrics.timeQuery("hentPensjonsgrunnlag") {
            """select * from grunnlag_pensjon where id=:id""".trimIndent()
                .hent(
                    mapOf(
                        "id" to id,
                    ),
                    session,
                ) {
                    it.toPensjonsgrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_pensjon where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toPensjonsgrunnlag(): Pensjonsgrunnlag {
        return Pensjonsgrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed")),
            pensjonsopplysninger = objectMapper.readValue<PensjonsopplysningerDb>((string("pensjonsopplysninger")))
                .toDomain(),
        )
    }

    private fun lagre(grunnlag: Pensjonsgrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_pensjon
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                pensjonsopplysninger
            ) values 
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                to_jsonb(:pensjonsopplysninger::jsonb)
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to grunnlag.id,
                    "opprettet" to grunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to grunnlag.periode.fraOgMed,
                    "tilOgMed" to grunnlag.periode.tilOgMed,
                    "pensjonsopplysninger" to objectMapper.writeValueAsString(grunnlag.pensjonsopplysninger.toDb()),
                ),
                tx,
            )
    }
}

internal fun PensjonsopplysningerDb.toDomain(): Pensjonsopplysninger {
    return Pensjonsopplysninger(
        folketrygd = Pensjonsopplysninger.Folketrygd(
            svar = folketrygd.toDomain(),
        ),
        andreNorske = Pensjonsopplysninger.AndreNorske(
            svar = andreNorske.toDomain(),
        ),
        utenlandske = Pensjonsopplysninger.Utenlandske(
            svar = utenlandske.toDomain(),
        ),
    )
}

internal fun Pensjonsopplysninger.toDb(): PensjonsopplysningerDb {
    return PensjonsopplysningerDb(
        folketrygd = folketrygd.svar.toDb(),
        andreNorske = andreNorske.svar.toDb(),
        utenlandske = utenlandske.svar.toDb(),
    )
}

internal fun PensjonsoppysningerSvarDb.toDomain(): Pensjonsopplysninger.Svar {
    return when (this) {
        PensjonsoppysningerSvarDb.JA -> {
            Pensjonsopplysninger.Svar.Ja
        }
        PensjonsoppysningerSvarDb.NEI -> {
            Pensjonsopplysninger.Svar.Nei
        }
        PensjonsoppysningerSvarDb.IKKE_AKTUELT -> {
            Pensjonsopplysninger.Svar.IkkeAktuelt
        }
    }
}

internal fun Pensjonsopplysninger.Svar.toDb(): PensjonsoppysningerSvarDb {
    return when (this) {
        Pensjonsopplysninger.Svar.IkkeAktuelt -> {
            PensjonsoppysningerSvarDb.IKKE_AKTUELT
        }
        Pensjonsopplysninger.Svar.Ja -> {
            PensjonsoppysningerSvarDb.JA
        }
        Pensjonsopplysninger.Svar.Nei -> {
            PensjonsoppysningerSvarDb.NEI
        }
    }
}

internal data class PensjonsopplysningerDb(
    val folketrygd: PensjonsoppysningerSvarDb,
    val andreNorske: PensjonsoppysningerSvarDb,
    val utenlandske: PensjonsoppysningerSvarDb,
)

internal enum class PensjonsoppysningerSvarDb {
    JA,
    NEI,
    IKKE_AKTUELT
}
