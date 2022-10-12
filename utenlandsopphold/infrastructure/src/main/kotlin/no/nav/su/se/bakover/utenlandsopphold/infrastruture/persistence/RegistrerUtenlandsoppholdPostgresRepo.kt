package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerUtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toDomain
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toJson
import java.util.UUID

const val RegistrertUtenlandsoppholdHendelsestype = "REGISTRERT_UTENLANDSOPPHOLD"

class RegistrerUtenlandsoppholdPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
) : RegistrerUtenlandsoppholdRepo {
    override fun lagre(
        hendelse: RegistrerUtenlandsoppholdHendelse,
    ) {
        hendelseRepo.persister(
            hendelse = hendelse,
            type = RegistrertUtenlandsoppholdHendelsestype,
            data = hendelse.toJson(),
        )
    }

    fun hentForSakId(
        sakId: UUID,
    ): List<RegistrertUtenlandsopphold> {
        return hendelseRepo.hentHendelserForSakIdOgType(sakId, RegistrertUtenlandsoppholdHendelsestype).toDomain()
    }
}
