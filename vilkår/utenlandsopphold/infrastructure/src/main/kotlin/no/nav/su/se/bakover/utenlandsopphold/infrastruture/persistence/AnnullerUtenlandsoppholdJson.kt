package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson.Companion.toIdentJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import vilkår.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse

internal data class AnnullerUtenlandsoppholdJson(
    val ident: IdentJson,
) {
    companion object {
        fun AnnullerUtenlandsoppholdHendelse.toJson(): String {
            return AnnullerUtenlandsoppholdJson(

                ident = this.utførtAv.toIdentJson(),
            ).let {
                serialize(it)
            }
        }

        fun PersistertHendelse.toAnnullertUtenlandsoppholdHendelse(): AnnullerUtenlandsoppholdHendelse {
            return deserialize<AnnullerUtenlandsoppholdJson>(this.data).let { json ->
                AnnullerUtenlandsoppholdHendelse.fraPersistert(
                    hendelseId = this.hendelseId,
                    tidligereHendelseId = this.tidligereHendelseId!!,
                    sakId = this.sakId!!,
                    utførtAv = json.ident.toDomain() as NavIdentBruker.Saksbehandler,
                    hendelsestidspunkt = this.hendelsestidspunkt,
                    versjon = this.versjon,
                    entitetId = this.entitetId,
                )
            }
        }
    }
}
