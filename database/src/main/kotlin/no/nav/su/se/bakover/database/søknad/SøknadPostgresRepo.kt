package no.nav.su.se.bakover.database.søknad

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.finnesBehandlingForSøknadInternal
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknadInternal
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.søknadAvsluttetOKInternal
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID
import javax.sql.DataSource

internal class SøknadPostgresRepo(
    private val dataSource: DataSource
) : SøknadRepo {
    override fun hentSøknad(søknadId: UUID) = dataSource.withSession { hentSøknadInternal(søknadId, it) }

    fun søknadAvsluttetOK(søknadId: UUID) = dataSource.withSession {
        søknadAvsluttetOKInternal(søknadId, it)
    }

    fun finnesBehandlingForSøknad(søknadId: UUID) = dataSource.withSession {
           finnesBehandlingForSøknadInternal(søknadId, it)
    }

    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        dataSource.withSession { session ->
            "insert into søknad (id, sakId, søknadInnhold, opprettet) values (:id, :sakId, to_json(:soknad::json), :opprettet)".oppdatering(
                mapOf(
                    "id" to søknad.id,
                    "sakId" to sakId,
                    "soknad" to objectMapper.writeValueAsString(søknad.søknadInnhold),
                    "opprettet" to søknad.opprettet
                ),
                session
            )
        }
        return hentSøknad(søknad.id)!!
    }

    override fun avsluttSøknadsBehandling(
        avsluttSøknadsBehandlingBody: AvsluttSøknadsBehandlingBody
    ): Either<KunneIkkeAvslutteSøknadsBehandling, AvsluttetSøknadsBehandlingOK> {

        //Det er mulig å url hacke routen med søknad id for å avslutte søknaden selv om det
        //finnes en behandling.
        if(finnesBehandlingForSøknad(avsluttSøknadsBehandlingBody.søknadId)){
            return KunneIkkeAvslutteSøknadsBehandling.left()
        }

        dataSource.withSession { session ->
            "update søknad set avsluttetBegrunnelse = :avsluttetBegrunnelse where id=:id".oppdatering(
                mapOf(
                    "id" to avsluttSøknadsBehandlingBody.søknadId,
                    "avsluttetBegrunnelse" to avsluttSøknadsBehandlingBody.avsluttSøkndsBehandlingBegrunnelse.toString()
                ),
                session
            )
        }
        return søknadAvsluttetOK(avsluttSøknadsBehandlingBody.søknadId)
    }
}
