package no.nav.su.se.bakover.institusjonsopphold.database

import kotliquery.Row
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelseDb.Companion.toDb
import java.lang.IllegalStateException
import java.time.Clock

class InstitusjonsoppholdHendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
) : InstitusjonsoppholdHendelseRepo {
    override fun lagre(hendelse: InstitusjonsoppholdHendelse) {
        lagre(hendelse.toDb())
    }

    override fun hentHendelserUtenOppgaveId(): List<InstitusjonsoppholdHendelse> {
        return dbMetrics.timeQuery("hentInstitusjonsoppholdHendelserUtenOppgave") {
            sessionFactory.withSession { session ->
                """
                    SELECT * FROM  institusjonsopphold_hendelse WHERE oppgaveId = null
                """.trimIndent().hentListe(mapOf(), session) {
                    it.toInstitusjonsoppholdHendelse()
                }
            }
        }
    }

    private fun lagre(hendelse: InstitusjonsoppholdHendelseDb) {
        dbMetrics.timeQuery("lagreInstitusjonsoppholdHendelse") {
            sessionFactory.withSession { session ->
                """
                    INSERT INTO
                        institusjonsopphold_hendelse
                        (id, opprettet, sakId, hendelsesId, oppholdId, norskIdent, type, kilde, oppgaveId)
                    VALUES 
                        (:id, :opprettet, :sakId, :hendelsesId, :oppholdId, :norskIdent, :type, :kilde, oppgaveId)
                """.trimIndent().insert(
                    mapOf(
                        "id" to hendelse.id,
                        "opprettet" to hendelse.opprettet,
                        "sakId" to hendelse.sakId,
                        "hendelsesId" to hendelse.hendelseId,
                        "oppholdId" to hendelse.oppholdId,
                        "norskIdent" to hendelse.norskident,
                        "type" to hendelse.type,
                        "kilde" to hendelse.kilde,
                        "oppgaveId" to hendelse.oppgaveId,
                    ),
                    session,
                )
            }
        }
    }

    private fun Row.toInstitusjonsoppholdHendelse(): InstitusjonsoppholdHendelse {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val sakId = uuid("sakId")
        val hendelsesId = long("hendelsesId")
        val oppholdId = long("oppholdId")
        val norskIdent = Fnr.tryCreate(string("norskIdent"))
            ?: throw IllegalStateException("Kunne ikke lage fødselsnummer for norsk ident for institusjonsoppholdHendelse $id")
        val type = InstitusjonsoppholdTypeDb.valueOf("type")
        val kilde = InstitusjonsoppholdKildeDb.valueOf("kilde")
        val oppgaveId = stringOrNull("oppgaveId")?.let { OppgaveId(it) }

        val hendelse = InstitusjonsoppholdHendelse.KnyttetTilSak.UtenOppgaveId(
            sakId = sakId,
            ikkeKnyttetTilSak = InstitusjonsoppholdHendelse.IkkeKnyttetTilSak(
                id,
                opprettet,
                EksternInstitusjonsoppholdHendelse(
                    hendelsesId,
                    oppholdId,
                    norskIdent,
                    type.toDomain(),
                    kilde.toDomain(),
                ),
            ),
        )

        return if (oppgaveId == null) hendelse else hendelse.knyttTilOppgaveId(oppgaveId)
    }
}
