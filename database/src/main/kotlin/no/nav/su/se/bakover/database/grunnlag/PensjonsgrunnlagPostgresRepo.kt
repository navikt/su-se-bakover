package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.pensjon.domain.Pensjonsgrunnlag
import vilkår.pensjon.domain.Pensjonsopplysninger
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
            pensjonsopplysninger = deserialize<PensjonsopplysningerDb>((string("pensjonsopplysninger"))).toDomain(),
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
                    "pensjonsopplysninger" to serialize(grunnlag.pensjonsopplysninger.toDb()),
                ),
                tx,
            )
    }
}

internal fun PensjonsopplysningerDb.toDomain(): Pensjonsopplysninger {
    return Pensjonsopplysninger(
        søktPensjonFolketrygd = Pensjonsopplysninger.SøktPensjonFolketrygd(
            svar = folketrygd.toDomain(),
        ),
        søktAndreNorskePensjoner = Pensjonsopplysninger.SøktAndreNorskePensjoner(
            svar = andreNorske.toDomain(),
        ),
        søktUtenlandskePensjoner = Pensjonsopplysninger.SøktUtenlandskePensjoner(
            svar = utenlandske.toDomain(),
        ),
    )
}

internal fun Pensjonsopplysninger.toDb(): PensjonsopplysningerDb {
    return PensjonsopplysningerDb(
        folketrygd = søktPensjonFolketrygd.svar.toDb(),
        andreNorske = søktAndreNorskePensjoner.svar.toDb(),
        utenlandske = søktUtenlandskePensjoner.svar.toDb(),
    )
}

private fun Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.toDb(): PensjonFraUtenlandskeSvarDb {
    return when (this) {
        Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarIkkeSøktUtenlandskePensjoner -> {
            PensjonFraUtenlandskeSvarDb.HAR_IKKE_SØKT_UTENLANDSKE_PENSJONER
        }
        Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarSøktUtenlandskePensjoner -> {
            PensjonFraUtenlandskeSvarDb.HAR_SØKT_UTENLANDSKE_PENSJONER
        }
        Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.IkkeAktuelt -> {
            PensjonFraUtenlandskeSvarDb.IKKE_AKTUELT
        }
    }
}

private fun Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.toDb(): PensjonFraAndreNorskeSvarDb {
    return when (this) {
        Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarSøktAndreNorskePensjonerEnnFolketrygden -> {
            PensjonFraAndreNorskeSvarDb.HAR_SØKT_ANDRE_NORSKE_PENSJONER_ENN_FOLKETRYGDEN
        }
        Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarIkkeSøktAndreNorskePensjonerEnnFolketrygden -> {
            PensjonFraAndreNorskeSvarDb.HAR_IKKE_SØKT_ANDRE_NORSKE_PENSJONER_ENN_FOLKETRYGDEN
        }
        Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.IkkeAktuelt -> {
            PensjonFraAndreNorskeSvarDb.IKKE_AKTUELT
        }
    }
}

private fun Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.toDb(): PensjonFraFolketrygdenSvarDb {
    return when (this) {
        Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarIkkeSøktPensjonFraFolketrygden -> {
            PensjonFraFolketrygdenSvarDb.HAR_IKKE_SØKT_PENSJON_FRA_FOLKETRYGDEN
        }
        Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden -> {
            PensjonFraFolketrygdenSvarDb.HAR_SØKT_PENSJON_FRA_FOLKETRYGDEN
        }
    }
}

internal data class PensjonsopplysningerDb(
    val folketrygd: PensjonFraFolketrygdenSvarDb,
    val andreNorske: PensjonFraAndreNorskeSvarDb,
    val utenlandske: PensjonFraUtenlandskeSvarDb,
)

internal enum class PensjonFraFolketrygdenSvarDb {
    HAR_IKKE_SØKT_PENSJON_FRA_FOLKETRYGDEN,
    HAR_SØKT_PENSJON_FRA_FOLKETRYGDEN,
    ;

    fun toDomain(): Pensjonsopplysninger.SøktPensjonFolketrygd.Svar {
        return when (this) {
            HAR_IKKE_SØKT_PENSJON_FRA_FOLKETRYGDEN -> {
                Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarIkkeSøktPensjonFraFolketrygden
            }
            HAR_SØKT_PENSJON_FRA_FOLKETRYGDEN -> {
                Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden
            }
        }
    }
}

internal enum class PensjonFraAndreNorskeSvarDb {
    HAR_IKKE_SØKT_ANDRE_NORSKE_PENSJONER_ENN_FOLKETRYGDEN,
    HAR_SØKT_ANDRE_NORSKE_PENSJONER_ENN_FOLKETRYGDEN,
    IKKE_AKTUELT,
    ;

    fun toDomain(): Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar {
        return when (this) {
            HAR_IKKE_SØKT_ANDRE_NORSKE_PENSJONER_ENN_FOLKETRYGDEN -> {
                Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarIkkeSøktAndreNorskePensjonerEnnFolketrygden
            }
            HAR_SØKT_ANDRE_NORSKE_PENSJONER_ENN_FOLKETRYGDEN -> {
                Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarSøktAndreNorskePensjonerEnnFolketrygden
            }
            IKKE_AKTUELT -> {
                Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.IkkeAktuelt
            }
        }
    }
}

internal enum class PensjonFraUtenlandskeSvarDb {
    HAR_IKKE_SØKT_UTENLANDSKE_PENSJONER,
    HAR_SØKT_UTENLANDSKE_PENSJONER,
    IKKE_AKTUELT,
    ;

    fun toDomain(): Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar {
        return when (this) {
            HAR_IKKE_SØKT_UTENLANDSKE_PENSJONER -> {
                Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarIkkeSøktUtenlandskePensjoner
            }
            HAR_SØKT_UTENLANDSKE_PENSJONER -> {
                Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarSøktUtenlandskePensjoner
            }
            IKKE_AKTUELT -> {
                Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.IkkeAktuelt
            }
        }
    }
}
