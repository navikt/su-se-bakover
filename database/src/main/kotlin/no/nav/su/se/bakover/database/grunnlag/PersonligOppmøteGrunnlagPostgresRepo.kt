package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.personligoppmøte.domain.PersonligOppmøteGrunnlag
import vilkår.personligoppmøte.domain.PersonligOppmøteÅrsak
import java.util.UUID

internal class PersonligOppmøteGrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, grunnlag: List<PersonligOppmøteGrunnlag>, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagrePersonligOppmøtegrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            grunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): PersonligOppmøteGrunnlag? {
        return dbMetrics.timeQuery("hentPersonligOppmøtegrunnlag") {
            """select * from grunnlag_personlig_oppmøte where id=:id""".trimIndent()
                .hent(
                    mapOf(
                        "id" to id,
                    ),
                    session,
                ) {
                    it.toPersonligOppmøteGrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_personlig_oppmøte where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toPersonligOppmøteGrunnlag(): PersonligOppmøteGrunnlag {
        return PersonligOppmøteGrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(
                localDate(columnLabel = "fraOgMed"),
                localDate("tilOgMed"),
            ),
            årsak = PersonligOppmøteÅrsakDb.valueOf(string("årsak")).toDomain(),
        )
    }

    private fun lagre(grunnlag: PersonligOppmøteGrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_personlig_oppmøte
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                årsak
            ) values
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                :aarsak
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to grunnlag.id,
                    "opprettet" to grunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to grunnlag.periode.fraOgMed,
                    "tilOgMed" to grunnlag.periode.tilOgMed,
                    "aarsak" to grunnlag.årsak.toDb(),
                ),
                tx,
            )
    }
}

internal enum class PersonligOppmøteÅrsakDb {
    MøttPersonlig,
    IkkeMøttMenVerge,
    IkkeMøttMenSykMedLegeerklæringOgFullmakt,
    IkkeMøttMenKortvarigSykMedLegeerklæring,
    IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
    IkkeMøttPersonlig,
    Uavklart,
}

internal fun PersonligOppmøteÅrsak.toDb(): String {
    return when (this) {
        PersonligOppmøteÅrsak.IkkeMøttMenKortvarigSykMedLegeerklæring -> PersonligOppmøteÅrsakDb.IkkeMøttMenKortvarigSykMedLegeerklæring
        PersonligOppmøteÅrsak.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt -> PersonligOppmøteÅrsakDb.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt
        PersonligOppmøteÅrsak.IkkeMøttMenSykMedLegeerklæringOgFullmakt -> PersonligOppmøteÅrsakDb.IkkeMøttMenSykMedLegeerklæringOgFullmakt
        PersonligOppmøteÅrsak.IkkeMøttMenVerge -> PersonligOppmøteÅrsakDb.IkkeMøttMenVerge
        PersonligOppmøteÅrsak.IkkeMøttPersonlig -> PersonligOppmøteÅrsakDb.IkkeMøttPersonlig
        PersonligOppmøteÅrsak.MøttPersonlig -> PersonligOppmøteÅrsakDb.MøttPersonlig
        PersonligOppmøteÅrsak.Uavklart -> PersonligOppmøteÅrsakDb.Uavklart
    }.toString()
}

internal fun PersonligOppmøteÅrsakDb.toDomain(): PersonligOppmøteÅrsak {
    return when (this) {
        PersonligOppmøteÅrsakDb.MøttPersonlig -> PersonligOppmøteÅrsak.MøttPersonlig
        PersonligOppmøteÅrsakDb.IkkeMøttMenVerge -> PersonligOppmøteÅrsak.IkkeMøttMenVerge
        PersonligOppmøteÅrsakDb.IkkeMøttMenSykMedLegeerklæringOgFullmakt -> PersonligOppmøteÅrsak.IkkeMøttMenSykMedLegeerklæringOgFullmakt
        PersonligOppmøteÅrsakDb.IkkeMøttMenKortvarigSykMedLegeerklæring -> PersonligOppmøteÅrsak.IkkeMøttMenKortvarigSykMedLegeerklæring
        PersonligOppmøteÅrsakDb.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt -> PersonligOppmøteÅrsak.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt
        PersonligOppmøteÅrsakDb.IkkeMøttPersonlig -> PersonligOppmøteÅrsak.IkkeMøttPersonlig
        PersonligOppmøteÅrsakDb.Uavklart -> PersonligOppmøteÅrsak.Uavklart
    }
}
