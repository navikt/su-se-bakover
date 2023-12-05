package no.nav.su.se.bakover.institusjonsopphold.database

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.domain.OppholdId
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toDbJson
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelseData.Companion.toStringifiedJson
import java.util.UUID

val InstitusjonsoppholdHendelsestype = Hendelsestype("INSTITUSJONSOPPHOLD")

class InstitusjonsoppholdHendelsePostgresRepo(
    private val dbMetrics: DbMetrics,
    private val hendelseRepo: HendelsePostgresRepo,
) : InstitusjonsoppholdHendelseRepo {

    override fun lagre(
        hendelse: InstitusjonsoppholdHendelse,
        meta: DefaultHendelseMetadata,
    ) {
        dbMetrics.timeQuery("lagreInstitusjonsoppholdHendelse") {
            hendelseRepo.persisterSakshendelse(
                hendelse = hendelse,
                meta = meta.toDbJson(),
                type = InstitusjonsoppholdHendelsestype,
                data = hendelse.toStringifiedJson(),
            )
        }
    }

    override fun hentForSak(sakId: UUID): InstitusjonsoppholdHendelserPåSak =
        dbMetrics.timeQuery("hentInstitusjonsoppholdHendelserPåSak") {
            hendelseRepo.hentHendelserForSakIdOgType(sakId, InstitusjonsoppholdHendelsestype).map {
                it.toInstitusjonsoppholdhendelse()
            }.toInstitusjonsoppholdHendelserPåSak()
        }

    override fun hentTidligereInstHendelserForOpphold(
        sakId: UUID,
        oppholdId: OppholdId,
    ): List<InstitusjonsoppholdHendelse> {
        return hendelseRepo.hentHendelserForSakIdOgType(
            sakId = sakId,
            type = InstitusjonsoppholdHendelsestype,
        ).toInstitusjonsoppholdhendelse().filter {
            it.eksterneHendelse.oppholdId == oppholdId
        }
    }

    private fun List<PersistertHendelse>.toInstitusjonsoppholdhendelse(): List<InstitusjonsoppholdHendelse> =
        this.map { it.toInstitusjonsoppholdhendelse() }

    private fun PersistertHendelse.toInstitusjonsoppholdhendelse(): InstitusjonsoppholdHendelse {
        val data = deserialize<InstitusjonsoppholdHendelseData>(this.data)

        return InstitusjonsoppholdHendelse(
            hendelseId = this.hendelseId,
            sakId = this.sakId
                ?: throw IllegalStateException("Institusjonsoppholdhendelse $hendelseId hadde sakId som null fra DB"),
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            eksterneHendelse = EksternInstitusjonsoppholdHendelse(
                hendelseId = data.hendelseId,
                oppholdId = OppholdId(data.oppholdId),
                norskident = Fnr.tryCreate(data.norskident)
                    ?: throw IllegalStateException("Kunne ikke lage Fnr for institusjonsoppholdhendelse $hendelseId"),
                type = data.type.toDomain(),
                kilde = data.kilde.toDomain(),
            ),
        )
    }

    private fun List<InstitusjonsoppholdHendelse>.toInstitusjonsoppholdHendelserPåSak(): InstitusjonsoppholdHendelserPåSak =
        InstitusjonsoppholdHendelserPåSak(hendelser = this.sorted().toNonEmptyList())
}
