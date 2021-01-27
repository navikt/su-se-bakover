package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepoInternal
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Saksbehandling
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID
import javax.sql.DataSource

interface SaksbehandlingRepo {
    fun lagre(saksbehandling: Saksbehandling)
    fun hent(id: UUID): Saksbehandling
    fun hentForSak(sakId: UUID, session: Session): List<Saksbehandling>
}

internal class SaksbehandlingsPostgresRepo(
    private val dataSource: DataSource
) : SaksbehandlingRepo {
    override fun lagre(saksbehandling: Saksbehandling) {
        when (saksbehandling) {
            is Saksbehandling.Søknadsbehandling -> {
                when (saksbehandling) {
                    is Saksbehandling.Søknadsbehandling.Opprettet -> lagre(saksbehandling)
                    is Saksbehandling.Søknadsbehandling.Vilkårsvurdert -> lagre(saksbehandling)
                    is Saksbehandling.Søknadsbehandling.Beregnet -> lagre(saksbehandling)
                    is Saksbehandling.Søknadsbehandling.Simulert -> lagre(saksbehandling)
                    is Saksbehandling.Søknadsbehandling.TilAttestering -> lagre(saksbehandling)
                    is Saksbehandling.Søknadsbehandling.Attestert -> lagre(saksbehandling)
                    else -> throw NotImplementedError()
                }
            }
        }
    }

    override fun hent(id: UUID): Saksbehandling {
        return dataSource.withSession { session ->
            "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
                .hent(mapOf("id" to id), session) { row ->
                    row.toSaksbehandling(session)
                }!!
        }
    }

    override fun hentForSak(sakId: UUID, session: Session): List<Saksbehandling> {
        return "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.sakId=:sakId"
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toSaksbehandling(session)
            }
    }

    private fun Row.toSaksbehandling(session: Session): Saksbehandling {
        val behandlingId = uuid("id")
        val søknad = SøknadRepoInternal.hentSøknadInternal(uuid("søknadId"), session)!!
        if (søknad !is Søknad.Journalført.MedOppgave) {
            throw IllegalStateException("Kunne ikke hente behandling med søknad som ikke er journalført med oppgave.")
        }
        val sakId = uuid("sakId")
        val opprettet = tidspunkt("opprettet")
        val behandlingsinformasjon = objectMapper.readValue<Behandlingsinformasjon>(string("behandlingsinformasjon"))
        val status = Behandling.BehandlingsStatus.valueOf(string("status"))
        val oppgaveId = OppgaveId(string("oppgaveId"))
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val attestering = stringOrNull("attestering")?.let { objectMapper.readValue<Attestering>(it) }
        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }
        val saksnummer = Saksnummer(long("saksnummer"))
        val hendelseslogg = HendelsesloggRepoInternal.hentHendelseslogg(behandlingId.toString(), session)
            ?: Hendelseslogg(
                behandlingId.toString()
            )
        val fnr = Fnr(string("fnr"))
        val iverksattJournalpostId = stringOrNull("iverksattJournalpostId")?.let { JournalpostId(it) }
        val iverksattBrevbestillingId = stringOrNull("iverksattBrevbestillingId")?.let { BrevbestillingId(it) }

        return when (status) {
            Behandling.BehandlingsStatus.OPPRETTET -> Saksbehandling.Søknadsbehandling.Opprettet(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Saksbehandling.Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Saksbehandling.Søknadsbehandling.Vilkårsvurdert.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            Behandling.BehandlingsStatus.BEREGNET_INNVILGET -> Saksbehandling.Søknadsbehandling.Beregnet.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!
            )
            Behandling.BehandlingsStatus.BEREGNET_AVSLAG -> Saksbehandling.Søknadsbehandling.Beregnet.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!
            )
            Behandling.BehandlingsStatus.SIMULERT -> Saksbehandling.Søknadsbehandling.Simulert(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!
            )
            Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!
            )
            Behandling.BehandlingsStatus.UNDERKJENT_INNVILGET -> Saksbehandling.Søknadsbehandling.Attestert.Underkjent(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!,
                attestering = attestering!!
            )
            Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> Saksbehandling.Søknadsbehandling.Attestert.Iverksatt.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!,
                attestering = attestering!!,
            )
            else -> throw NotImplementedError()
        }
    }

    private fun lagre(saksbehandling: Saksbehandling.Søknadsbehandling.Opprettet) {
        dataSource.withSession { session ->
            (
                """
                   insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                   values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, null, null, null, null) 
                   on conflict (id) do
                   update set behandlingsinformasjon = to_json(:behandlingsinformasjon::json)
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to saksbehandling.id,
                    "sakId" to saksbehandling.sakId,
                    "soknadId" to saksbehandling.søknad.id,
                    "opprettet" to saksbehandling.opprettet,
                    "status" to saksbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(saksbehandling.behandlingsinformasjon),
                    "oppgaveId" to saksbehandling.oppgaveId.toString()
                ),
                session
            )
        }
    }

    private fun lagre(saksbehandling: Saksbehandling.Søknadsbehandling.Vilkårsvurdert) {
        dataSource.withSession { session ->
            (
                """
                   insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                   values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, null, null, null, null) 
                   on conflict (id) do
                   update set status = :status, behandlingsinformasjon = to_json(:behandlingsinformasjon::json), beregning = null, simulering = null
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to saksbehandling.id,
                    "sakId" to saksbehandling.sakId,
                    "soknadId" to saksbehandling.søknad.id,
                    "opprettet" to saksbehandling.opprettet,
                    "status" to saksbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(saksbehandling.behandlingsinformasjon),
                    "oppgaveId" to saksbehandling.oppgaveId.toString()
                ),
                session
            )
        }
    }

    private fun lagre(saksbehandling: Saksbehandling.Søknadsbehandling.Beregnet) {
        dataSource.withSession { session ->
            (
                """
                   insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                   values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), null, null, null) 
                   on conflict (id) do
                   update set status = :status, beregning = to_json(:beregning::json), simulering = null
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to saksbehandling.id,
                    "sakId" to saksbehandling.sakId,
                    "soknadId" to saksbehandling.søknad.id,
                    "opprettet" to saksbehandling.opprettet,
                    "status" to saksbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(saksbehandling.behandlingsinformasjon),
                    "oppgaveId" to saksbehandling.oppgaveId.toString(),
                    "beregning" to objectMapper.writeValueAsString(saksbehandling.beregning.toSnapshot())
                ),
                session
            )
        }
    }

    private fun lagre(saksbehandling: Saksbehandling.Søknadsbehandling.Simulert) {
        dataSource.withSession { session ->
            (
                """
                   insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                   values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), to_json(:simulering::json), null, null) 
                   on conflict (id) do
                   update set status = :status, simulering = to_json(:simulering::json)
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to saksbehandling.id,
                    "sakId" to saksbehandling.sakId,
                    "soknadId" to saksbehandling.søknad.id,
                    "opprettet" to saksbehandling.opprettet,
                    "status" to saksbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(saksbehandling.behandlingsinformasjon),
                    "oppgaveId" to saksbehandling.oppgaveId.toString(),
                    "beregning" to objectMapper.writeValueAsString(saksbehandling.beregning.toSnapshot()),
                    "simulering" to objectMapper.writeValueAsString(saksbehandling.simulering)
                ),
                session
            )
        }
    }

    private fun lagre(saksbehandling: Saksbehandling.Søknadsbehandling.TilAttestering) {
        when (saksbehandling) {
            is Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget -> {
                dataSource.withSession { session ->
                    (
                        """
                       insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                       values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, null) 
                       on conflict (id) do
                       update set status = :status, saksbehandler = :saksbehandler, attestering = null
                        """.trimIndent()
                        ).oppdatering(
                        mapOf(
                            "id" to saksbehandling.id,
                            "sakId" to saksbehandling.sakId,
                            "soknadId" to saksbehandling.søknad.id,
                            "opprettet" to saksbehandling.opprettet,
                            "status" to saksbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(saksbehandling.behandlingsinformasjon),
                            "oppgaveId" to saksbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(saksbehandling.beregning.toSnapshot()),
                            "simulering" to objectMapper.writeValueAsString(saksbehandling.simulering),
                            "saksbehandler" to saksbehandling.saksbehandler.navIdent
                        ),
                        session
                    )
                }
            }
        }
    }

    private fun lagre(saksbehandling: Saksbehandling.Søknadsbehandling.Attestert) {
        when (saksbehandling) {
            is Saksbehandling.Søknadsbehandling.Attestert.Underkjent -> {
                dataSource.withSession { session ->
                    (
                        """
                       insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                       values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, to_json(:attestering::json)) 
                       on conflict (id) do
                       update set status = :status, attestering = to_json(:attestering::json)
                        """.trimIndent()
                        ).oppdatering(
                        mapOf(
                            "id" to saksbehandling.id,
                            "sakId" to saksbehandling.sakId,
                            "soknadId" to saksbehandling.søknad.id,
                            "opprettet" to saksbehandling.opprettet,
                            "status" to saksbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(saksbehandling.behandlingsinformasjon),
                            "oppgaveId" to saksbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(saksbehandling.beregning.toSnapshot()),
                            "simulering" to objectMapper.writeValueAsString(saksbehandling.simulering),
                            "saksbehandler" to saksbehandling.saksbehandler.navIdent,
                            "attestering" to objectMapper.writeValueAsString(saksbehandling.attestering)
                        ),
                        session
                    )
                }
            }
            is Saksbehandling.Søknadsbehandling.Attestert.Iverksatt -> {
                when (saksbehandling) {
                    is Saksbehandling.Søknadsbehandling.Attestert.Iverksatt.OversendtOppdrag -> {
                        dataSource.withSession { session ->
                            (
                                """
                       insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering, utbetalingId) 
                       values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, to_json(:attestering::json), utbetalingId) 
                       on conflict (id) do
                       update set status = :status, attestering = to_json(:attestering::json), utbetalingId = :utbetalingId
                                """.trimIndent()
                                ).oppdatering(
                                mapOf(
                                    "id" to saksbehandling.id,
                                    "sakId" to saksbehandling.sakId,
                                    "soknadId" to saksbehandling.søknad.id,
                                    "opprettet" to saksbehandling.opprettet,
                                    "status" to saksbehandling.status.name,
                                    "behandlingsinformasjon" to objectMapper.writeValueAsString(saksbehandling.behandlingsinformasjon),
                                    "oppgaveId" to saksbehandling.oppgaveId.toString(),
                                    "beregning" to objectMapper.writeValueAsString(saksbehandling.beregning.toSnapshot()),
                                    "simulering" to objectMapper.writeValueAsString(saksbehandling.simulering),
                                    "saksbehandler" to saksbehandling.saksbehandler.navIdent,
                                    "attestering" to objectMapper.writeValueAsString(saksbehandling.attestering),
                                    "utbetalingId" to saksbehandling.utbetaling.id
                                ),
                                session
                            )
                        }
                    }
                    else -> throw NotImplementedError()
                }
            }
        }
    }
}
