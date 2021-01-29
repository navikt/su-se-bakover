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
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID
import javax.sql.DataSource

interface SaksbehandlingRepo {
    fun lagre(søknadsbehandling: Søknadsbehandling)
    fun hent(id: UUID): Søknadsbehandling?
    fun hentForSak(sakId: UUID, session: Session): List<Søknadsbehandling>
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
}

internal class SaksbehandlingsPostgresRepo(
    private val dataSource: DataSource
) : SaksbehandlingRepo {
    override fun lagre(søknadsbehandling: Søknadsbehandling) {
        when (søknadsbehandling) {
            is Søknadsbehandling.Opprettet -> lagre(søknadsbehandling)
            is Søknadsbehandling.Vilkårsvurdert -> lagre(søknadsbehandling)
            is Søknadsbehandling.Beregnet -> lagre(søknadsbehandling)
            is Søknadsbehandling.Simulert -> lagre(søknadsbehandling)
            is Søknadsbehandling.TilAttestering -> lagre(søknadsbehandling)
            is Søknadsbehandling.Underkjent -> lagre(søknadsbehandling)
            is Søknadsbehandling.Iverksatt -> lagre(søknadsbehandling)
        }
    }

    override fun hentEventuellTidligereAttestering(id: UUID): Attestering? {
        return dataSource.withSession { session ->
            "select b.attestering from behandling b where b.id=:id"
                .hent(mapOf("id" to id), session) { row ->
                    row.stringOrNull("attestering")?.let {
                        objectMapper.readValue(it)
                    }
                }
        }
    }

    override fun hent(id: UUID): Søknadsbehandling? {
        return dataSource.withSession { session ->
            "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
                .hent(mapOf("id" to id), session) { row ->
                    row.toSaksbehandling(session)
                }
        }
    }

    override fun hentForSak(sakId: UUID, session: Session): List<Søknadsbehandling> {
        return "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.sakId=:sakId"
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toSaksbehandling(session)
            }
    }

    private fun Row.toSaksbehandling(session: Session): Søknadsbehandling {
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
        val utbetalingId = uuid30("utbetalingId")

        @Suppress("UNUSED_VARIABLE")
        val hendelseslogg = HendelsesloggRepoInternal.hentHendelseslogg(behandlingId.toString(), session)
            ?: Hendelseslogg(
                behandlingId.toString()
            )
        val fnr = Fnr(string("fnr"))

        @Suppress("UNUSED_VARIABLE")
        val iverksattJournalpostId = stringOrNull("iverksattJournalpostId")?.let { JournalpostId(it) }

        @Suppress("UNUSED_VARIABLE")
        val iverksattBrevbestillingId = stringOrNull("iverksattBrevbestillingId")?.let { BrevbestillingId(it) }

        val eksterneIverksettingsteg = when {
            iverksattJournalpostId != null && iverksattBrevbestillingId != null -> Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                journalpostId = iverksattJournalpostId,
                brevbestillingId = iverksattBrevbestillingId
            )
            iverksattJournalpostId != null -> Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.Journalført(
                iverksattJournalpostId
            )
            else -> throw IllegalStateException("Kunne ikke bestemme eksterne iverksettingssteg for avslag, iverksattJournalpostId:$iverksattJournalpostId, iverksattBrevbestillingId:$iverksattBrevbestillingId")
        }

        return when (status) {
            Behandling.BehandlingsStatus.OPPRETTET -> Søknadsbehandling.Opprettet(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Søknadsbehandling.Vilkårsvurdert.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            Behandling.BehandlingsStatus.BEREGNET_INNVILGET -> Søknadsbehandling.Beregnet.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!
            )
            Behandling.BehandlingsStatus.BEREGNET_AVSLAG -> Søknadsbehandling.Beregnet.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!
            )
            Behandling.BehandlingsStatus.SIMULERT -> Søknadsbehandling.Simulert(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!
            )
            Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> Søknadsbehandling.TilAttestering.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!
            )
            Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> when (beregning) {
                null -> Søknadsbehandling.TilAttestering.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    saksbehandler = saksbehandler!!
                )
                else -> Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler!!
                )
            }
            Behandling.BehandlingsStatus.UNDERKJENT_INNVILGET -> Søknadsbehandling.Underkjent.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!,
                attestering = attestering!!
            )
            Behandling.BehandlingsStatus.UNDERKJENT_AVSLAG -> when (beregning) {
                null -> Søknadsbehandling.Underkjent.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    saksbehandler = saksbehandler!!,
                    attestering = attestering!!
                )
                else -> Søknadsbehandling.Underkjent.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler!!,
                    attestering = attestering!!
                )
            }
            Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> Søknadsbehandling.Iverksatt.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!,
                attestering = attestering!!,
                utbetalingId = utbetalingId
            )
            Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> when (beregning) {
                null -> Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    saksbehandler = saksbehandler!!,
                    attestering = attestering!!,
                    eksterneIverkssettingsteg = eksterneIverksettingsteg
                )
                else -> Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler!!,
                    attestering = attestering!!,
                    eksterneIverkssettingsteg = eksterneIverksettingsteg
                )
            }
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Opprettet) {
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
                    "id" to søknadsbehandling.id,
                    "sakId" to søknadsbehandling.sakId,
                    "soknadId" to søknadsbehandling.søknad.id,
                    "opprettet" to søknadsbehandling.opprettet,
                    "status" to søknadsbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                    "oppgaveId" to søknadsbehandling.oppgaveId.toString()
                ),
                session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert) {
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
                    "id" to søknadsbehandling.id,
                    "sakId" to søknadsbehandling.sakId,
                    "soknadId" to søknadsbehandling.søknad.id,
                    "opprettet" to søknadsbehandling.opprettet,
                    "status" to søknadsbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                    "oppgaveId" to søknadsbehandling.oppgaveId.toString()
                ),
                session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Beregnet) {
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
                    "id" to søknadsbehandling.id,
                    "sakId" to søknadsbehandling.sakId,
                    "soknadId" to søknadsbehandling.søknad.id,
                    "opprettet" to søknadsbehandling.opprettet,
                    "status" to søknadsbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                    "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                    "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot())
                ),
                session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Simulert) {
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
                    "id" to søknadsbehandling.id,
                    "sakId" to søknadsbehandling.sakId,
                    "soknadId" to søknadsbehandling.søknad.id,
                    "opprettet" to søknadsbehandling.opprettet,
                    "status" to søknadsbehandling.status.name,
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                    "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                    "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                    "simulering" to objectMapper.writeValueAsString(søknadsbehandling.simulering)
                ),
                session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.TilAttestering) {
        when (søknadsbehandling) {
            is Søknadsbehandling.TilAttestering.Innvilget -> {
                dataSource.withSession { session ->
                    (
                        """
                       insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                       values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, null) 
                       on conflict (id) do
                       update set status = :status, saksbehandler = :saksbehandler
                        """.trimIndent()
                        ).oppdatering(
                        mapOf(
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                            "simulering" to objectMapper.writeValueAsString(søknadsbehandling.simulering),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent
                        ),
                        session
                    )
                }
            }
            is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
                dataSource.withSession { session ->
                    (
                        """
                       insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                       values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, null) 
                       on conflict (id) do
                       update set status = :status, saksbehandler = :saksbehandler
                        """.trimIndent()
                        ).oppdatering(
                        mapOf(
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent
                        ),
                        session
                    )
                }
            }
            is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
                dataSource.withSession { session ->
                    (
                        """
                       insert into behandling (id, sakId, søknadId, opprettet, status, behandlingsinformasjon, oppgaveId, beregning, simulering, saksbehandler, attestering) 
                       values (:id, :sakId, :soknadId, :opprettet, :status, to_json(:behandlingsinformasjon::json), :oppgaveId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, null) 
                       on conflict (id) do
                       update set status = :status, saksbehandler = :saksbehandler
                        """.trimIndent()
                        ).oppdatering(
                        mapOf(
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent
                        ),
                        session
                    )
                }
            }
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Underkjent) {
        when (søknadsbehandling) {
            is Søknadsbehandling.Underkjent.Innvilget -> {
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
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                            "simulering" to objectMapper.writeValueAsString(søknadsbehandling.simulering),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                            "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering)
                        ),
                        session
                    )
                }
            }
            is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
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
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                            "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering)
                        ),
                        session
                    )
                }
            }
            is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
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
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                            "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering)
                        ),
                        session
                    )
                }
            }
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Iverksatt) {
        when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> {
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
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                            "simulering" to objectMapper.writeValueAsString(søknadsbehandling.simulering),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                            "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering)
                        ),
                        session
                    )
                }
            }
            is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
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
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                            "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering)
                        ),
                        session
                    )
                }
            }
            is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
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
                            "id" to søknadsbehandling.id,
                            "sakId" to søknadsbehandling.sakId,
                            "soknadId" to søknadsbehandling.søknad.id,
                            "opprettet" to søknadsbehandling.opprettet,
                            "status" to søknadsbehandling.status.name,
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                            "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                            "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                            "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering)
                        ),
                        session
                    )
                }
            }
        }
    }
}
