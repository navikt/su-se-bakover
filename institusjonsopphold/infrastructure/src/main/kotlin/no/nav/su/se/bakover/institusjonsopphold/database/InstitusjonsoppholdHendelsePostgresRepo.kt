package no.nav.su.se.bakover.institusjonsopphold.database

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelseData.Companion.toStringifiedJson
import java.lang.IllegalStateException
import java.time.Clock
import java.util.UUID

private const val InstitusjonsoppholdHendelseUtenOppgaveId = "INSTITUSJONSOPPHOLD_UTEN_OPPGAVEID"
private const val InstitusjonsoppholdHendelseMedOppgaveId = "INSTITUSJONSOPPHOLD_MED_OPPGAVEID"

private val alleTyper =
    nonEmptyListOf(InstitusjonsoppholdHendelseUtenOppgaveId, InstitusjonsoppholdHendelseMedOppgaveId)

class InstitusjonsoppholdHendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val hendelseRepo: HendelsePostgresRepo,
    private val clock: Clock,
) : InstitusjonsoppholdHendelseRepo {
    override fun lagre(hendelse: InstitusjonsoppholdHendelse) {
        when (hendelse) {
            is InstitusjonsoppholdHendelse.MedOppgaveId -> lagre(hendelse)
            is InstitusjonsoppholdHendelse.UtenOppgaveId -> lagre(hendelse)
        }
    }

    private fun lagre(hendelse: InstitusjonsoppholdHendelse.UtenOppgaveId) {
        dbMetrics.timeQuery("lagreInstitusjonsoppholdHendelse-utenOppgaveId") {
            hendelseRepo.persister(
                hendelse = hendelse,
                type = InstitusjonsoppholdHendelseUtenOppgaveId,
                data = hendelse.toStringifiedJson(),
            )
        }
    }

    private fun lagre(hendelse: InstitusjonsoppholdHendelse.MedOppgaveId) {
        dbMetrics.timeQuery("lagreInstitusjonsoppholdHendelse-medOppgaveId") {
            hendelseRepo.persister(
                hendelse = hendelse,
                type = InstitusjonsoppholdHendelseMedOppgaveId,
                data = hendelse.toStringifiedJson(),
            )
        }
    }

    override fun hentForSak(sakId: UUID): InstitusjonsoppholdHendelserPåSak =
        dbMetrics.timeQuery("hentInstitusjonsoppholdHendelse") {
            hendelseRepo.hentHendelserForSakIdOgTyper(sakId, alleTyper).map {
                it.toInstitusjonsoppholdhendelse()
            }.toInstitusjonsoppholdHendelserPåSak()
        }

    override fun hentSisteVersjonFor(sakId: UUID): Hendelsesversjon? =
        hendelseRepo.hentSisteHendelseforSakIdOgTyper(sakId, alleTyper)?.versjon

    override fun hentHendelserUtenOppgaveId(): List<InstitusjonsoppholdHendelse.UtenOppgaveId> {
        return dbMetrics.timeQuery("hentInstitusjonsoppholdHendelserUtenOppgave") {
            val hendelser = hendelseRepo.hentSisteHendelseForAlleSakerPåTyper(alleTyper) ?: return@timeQuery emptyList()

            hendelser.map { it.toInstitusjonsoppholdhendelse() }
                .groupBy { it.sakId }
                .tilHendelserPåSak()
                .flatMap { it.hentHendelserMedBehovForOppgaveId() }
        }
    }

    private fun PersistertHendelse.toInstitusjonsoppholdhendelse(): InstitusjonsoppholdHendelse {
        return when (this.type) {
            InstitusjonsoppholdHendelseUtenOppgaveId -> this.toInstitusjonsoppholdhendelseUtenOppgaveId()
            InstitusjonsoppholdHendelseMedOppgaveId -> this.toInstitusjonsoppholdhendelseMedOppgaveId()
            else -> throw IllegalStateException("Ukjent institusjonsopphold hendelse type")
        }
    }

    private fun PersistertHendelse.toInstitusjonsoppholdhendelseUtenOppgaveId(): InstitusjonsoppholdHendelse.UtenOppgaveId {
        val data = deserialize<InstitusjonsoppholdHendelseData>(this.data)
        require(data.oppgaveId == null) {
            "Prøve å lage institusjonsoppholdUtenOppgaveId på en hendelse ${this.hendelseId} der oppgaveId fantes"
        }

        return InstitusjonsoppholdHendelse.UtenOppgaveId(
            hendelseId = HendelseId.fromUUID(this.hendelseId),
            sakId = this.sakId
                ?: throw IllegalStateException("Institusjonsoppholdhendelse $hendelseId hadde sakId som null fra DB"),
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            eksterneHendelse = EksternInstitusjonsoppholdHendelse(
                hendelseId = data.hendelseId,
                oppholdId = data.oppholdId,
                norskident = Fnr.tryCreate(data.norskident)
                    ?: throw IllegalStateException("Kunne ikke lage Fnr for institusjonsoppholdhendelse $hendelseId"),
                type = data.type.toDomain(),
                kilde = data.kilde.toDomain(),
            ),
        )
    }

    private fun PersistertHendelse.toInstitusjonsoppholdhendelseMedOppgaveId(): InstitusjonsoppholdHendelse.MedOppgaveId {
        val data = deserialize<InstitusjonsoppholdHendelseData>(this.data)
        require(data.oppgaveId != null) {
            "Prøve å lage institusjonsoppholdMedOppgaveId på en hendelse ${this.hendelseId} der oppgaveId ikke fantes"
        }

        return InstitusjonsoppholdHendelse.MedOppgaveId(
            hendelseId = HendelseId.fromUUID(this.hendelseId),
            oppgaveId = OppgaveId(data.oppgaveId),
            hendelsestidspunkt = this.hendelsestidspunkt,
            tidligereHendelseId = HendelseId.fromUUID(this.tidligereHendelseId!!),
            versjon = this.versjon,
            sakId = this.sakId
                ?: throw IllegalStateException("Institusjonsoppholdhendelse $hendelseId hadde sakId som null fra DB"),
            eksterneHendelse = EksternInstitusjonsoppholdHendelse(
                hendelseId = data.hendelseId,
                oppholdId = data.oppholdId,
                norskident = Fnr.tryCreate(data.norskident)
                    ?: throw IllegalStateException("Kunne ikke lage Fnr for institusjonsoppholdhendelse $hendelseId"),
                type = data.type.toDomain(),
                kilde = data.kilde.toDomain(),
            ),
        )
    }

    private fun Map<UUID, List<InstitusjonsoppholdHendelse>>.tilHendelserPåSak(): List<InstitusjonsoppholdHendelserPåSak> =
        this.entries.map { InstitusjonsoppholdHendelserPåSak(it.value.sorted().toNonEmptyList()) }

    private fun List<InstitusjonsoppholdHendelse>.toInstitusjonsoppholdHendelserPåSak(): InstitusjonsoppholdHendelserPåSak =
        InstitusjonsoppholdHendelserPåSak(hendelser = this.sorted().toNonEmptyList())
}
