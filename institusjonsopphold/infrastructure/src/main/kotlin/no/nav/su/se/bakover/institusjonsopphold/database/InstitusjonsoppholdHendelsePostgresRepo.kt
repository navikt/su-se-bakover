package no.nav.su.se.bakover.institusjonsopphold.database

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.deserialize
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

const val InstitusjonsoppholdHendelsestype = "INSTITUSJONSOPPHOLD"

private val alleTyper = nonEmptyListOf(InstitusjonsoppholdHendelsestype)

class InstitusjonsoppholdHendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val hendelseRepo: HendelsePostgresRepo,
    private val clock: Clock,
) : InstitusjonsoppholdHendelseRepo {

    override fun lagre(hendelse: InstitusjonsoppholdHendelse) {
        dbMetrics.timeQuery("lagreInstitusjonsoppholdHendelse") {
            hendelseRepo.persister(
                hendelse = hendelse,
                type = InstitusjonsoppholdHendelsestype,
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

    private fun PersistertHendelse.toInstitusjonsoppholdhendelse(): InstitusjonsoppholdHendelse {
        val data = deserialize<InstitusjonsoppholdHendelseData>(this.data)

        return InstitusjonsoppholdHendelse(
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

    private fun Map<UUID, List<InstitusjonsoppholdHendelse>>.tilHendelserPåSak(): List<InstitusjonsoppholdHendelserPåSak> =
        this.entries.map { InstitusjonsoppholdHendelserPåSak(it.value.sorted().toNonEmptyList()) }

    private fun List<InstitusjonsoppholdHendelse>.toInstitusjonsoppholdHendelserPåSak(): InstitusjonsoppholdHendelserPåSak =
        InstitusjonsoppholdHendelserPåSak(hendelser = this.sorted().toNonEmptyList())
}
