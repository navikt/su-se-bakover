package no.nav.su.se.bakover.database.søknad

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.AvsluttSøkndsBehandlingBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

internal object SøknadRepoInternal {
    fun hentSøknadInternal(søknadId: UUID, session: Session): Søknad? = "select * from søknad where id=:id"
        .hent(mapOf("id" to søknadId), session) {
            it.toSøknad()
        }

    fun hentSøknaderInternal(sakId: UUID, session: Session) = "select * from søknad where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toSøknad()
        }.toMutableList()

    fun søknadAvsluttetOKInternal(
        søknadId: UUID,
        session: Session
    ): Either<KunneIkkeAvslutteSøknadsBehandling, AvsluttetSøknadsBehandlingOK> {
        val avsluttetBegrunnelse = "select avsluttetBegrunnelse from søknad where id=:id".hent(
            mapOf("id" to søknadId), session
        ) {
            it.stringOrNull("avsluttetBegrunnelse")
        }

        if (
            avsluttetBegrunnelse == AvsluttSøkndsBehandlingBegrunnelse.Trukket.toString() ||
            avsluttetBegrunnelse == AvsluttSøkndsBehandlingBegrunnelse.AvvistSøktForTidlig.toString() ||
            avsluttetBegrunnelse == AvsluttSøkndsBehandlingBegrunnelse.Bortfalt.toString()
        ) {
            return AvsluttetSøknadsBehandlingOK.right()
        }
        return KunneIkkeAvslutteSøknadsBehandling.left()
    }
}

internal fun toAvsluttetBegrunnelse(string: String?): AvsluttSøkndsBehandlingBegrunnelse? {
    return when (string) {
        AvsluttSøkndsBehandlingBegrunnelse.Trukket.toString() -> AvsluttSøkndsBehandlingBegrunnelse.Trukket
        AvsluttSøkndsBehandlingBegrunnelse.Bortfalt.toString() -> AvsluttSøkndsBehandlingBegrunnelse.Bortfalt
        AvsluttSøkndsBehandlingBegrunnelse.AvvistSøktForTidlig.toString() -> AvsluttSøkndsBehandlingBegrunnelse.AvvistSøktForTidlig
        else -> null
    }
}

internal fun Row.toSøknad(): Søknad {
    return Søknad(
        id = uuid("id"),
        søknadInnhold = objectMapper.readValue(string("søknadInnhold")),
        opprettet = tidspunkt("opprettet"),
        avsluttetBegrunnelse = toAvsluttetBegrunnelse(stringOrNull("avsluttetBegrunnelse"))
    )
}
