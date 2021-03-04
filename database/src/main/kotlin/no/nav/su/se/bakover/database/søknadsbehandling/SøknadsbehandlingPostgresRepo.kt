package no.nav.su.se.bakover.database.søknadsbehandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.EksterneIverksettingsstegEtterUtbetalingMapper
import no.nav.su.se.bakover.database.EksterneIverksettingsstegForAvslagMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID
import javax.sql.DataSource

internal class SøknadsbehandlingPostgresRepo(
    private val dataSource: DataSource
) : SøknadsbehandlingRepo {
    override fun lagre(søknadsbehandling: Søknadsbehandling) {
        when (søknadsbehandling) {
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

    override fun hent(id: UUID, session: Session): Søknadsbehandling? {
        return dataSource.withSession(session) { s ->
            "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
                .hent(mapOf("id" to id), s) { row ->
                    row.toSaksbehandling(s)
                }
        }
    }

    override fun hentForSak(sakId: UUID, session: Session): List<Søknadsbehandling> {
        return "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.sakId=:sakId"
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toSaksbehandling(session)
            }
    }

    override fun hentIverksatteBehandlingerUtenJournalposteringer(): List<Søknadsbehandling.Iverksatt.Innvilget> {
        return dataSource.withSession { session ->
            "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where iverksattJournalpostId is null and status = 'IVERKSATT_INNVILGET'"
                .hentListe(emptyMap(), session) { row ->
                    row.toSaksbehandling(session)
                }.filterIsInstance<Søknadsbehandling.Iverksatt.Innvilget>()
        }
    }

    override fun hentIverksatteBehandlingerUtenBrevbestillinger(): List<Søknadsbehandling.Iverksatt> {
        return dataSource.withSession { session ->
            "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where iverksattJournalpostId is not null and iverksattBrevbestillingId is null and status like 'IVERKSATT_%'"
                .hentListe(emptyMap(), session) { row ->
                    row.toSaksbehandling(session)
                }.filterIsInstance<Søknadsbehandling.Iverksatt>()
        }
    }

    override fun hentBehandlingForUtbetaling(utbetalingId: UUID30): Søknadsbehandling.Iverksatt.Innvilget? {
        return dataSource.withSession { session ->
            "select b.*, s.fnr, s.saksnummer from utbetaling u inner join behandling b on b.utbetalingid = u.id inner join sak s on s.id = b.sakid where u.id = :id"
                .hent(mapOf("id" to utbetalingId), session) { row ->
                    row.toSaksbehandling(session) as Søknadsbehandling.Iverksatt.Innvilget
                }
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
        val status = BehandlingsStatus.valueOf(string("status"))
        val oppgaveId = OppgaveId(string("oppgaveId"))
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val attestering = stringOrNull("attestering")?.let { objectMapper.readValue<Attestering>(it) }
        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }
        val saksnummer = Saksnummer(long("saksnummer"))

        val fnr = Fnr(string("fnr"))

        val iverksattJournalpostId = stringOrNull("iverksattJournalpostId")?.let { JournalpostId(it) }

        val iverksattBrevbestillingId = stringOrNull("iverksattBrevbestillingId")?.let { BrevbestillingId(it) }

        return when (status) {
            BehandlingsStatus.OPPRETTET -> Søknadsbehandling.Vilkårsvurdert.Uavklart(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Søknadsbehandling.Vilkårsvurdert.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr
            )
            BehandlingsStatus.BEREGNET_INNVILGET -> Søknadsbehandling.Beregnet.Innvilget(
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
            BehandlingsStatus.BEREGNET_AVSLAG -> Søknadsbehandling.Beregnet.Avslag(
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
            BehandlingsStatus.SIMULERT -> Søknadsbehandling.Simulert(
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
            BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> Søknadsbehandling.TilAttestering.Innvilget(
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
            BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> when (beregning) {
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
            BehandlingsStatus.UNDERKJENT_INNVILGET -> Søknadsbehandling.Underkjent.Innvilget(
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
            BehandlingsStatus.UNDERKJENT_AVSLAG -> when (beregning) {
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
            BehandlingsStatus.IVERKSATT_INNVILGET -> {
                val eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetalingMapper.idToObject(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
                Søknadsbehandling.Iverksatt.Innvilget(
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
                    utbetalingId = uuid30("utbetalingId"),
                    eksterneIverksettingsteg = eksterneIverksettingsteg,
                )
            }
            BehandlingsStatus.IVERKSATT_AVSLAG -> {
                val eksterneIverksettingsteg = EksterneIverksettingsstegForAvslagMapper.idToObject(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
                when (beregning) {
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
                        eksterneIverksettingsteg = eksterneIverksettingsteg
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
                        eksterneIverksettingsteg = eksterneIverksettingsteg
                    )
                }
            }
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
                ).oppdatering(defaultParams(søknadsbehandling), session)
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Beregnet) {
        dataSource.withSession { session ->
            (
                """
                   update behandling set status = :status, beregning = to_json(:beregning::json), simulering = null where id = :id
                """.trimIndent()
                ).oppdatering(
                params = defaultParams(søknadsbehandling).plus(
                    "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot())
                ),
                session = session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Simulert) {
        dataSource.withSession { session ->
            (
                """
                   update behandling set status = :status, beregning = to_json(:beregning::json), simulering = to_json(:simulering::json)  where id = :id
                """.trimIndent()
                ).oppdatering(
                defaultParams(søknadsbehandling).plus(
                    listOf(
                        "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                        "simulering" to objectMapper.writeValueAsString(søknadsbehandling.simulering)
                    )
                ),
                session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.TilAttestering) {
        dataSource.withSession { session ->
            (
                """
                   update behandling set status = :status, saksbehandler = :saksbehandler, oppgaveId = :oppgaveId  where id = :id
                """.trimIndent()
                ).oppdatering(
                defaultParams(søknadsbehandling).plus(
                    "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                ),
                session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Underkjent) {
        dataSource.withSession { session ->
            (
                """
                    update behandling set status = :status, attestering = to_json(:attestering::json), oppgaveId = :oppgaveId  where id = :id
                """.trimIndent()
                ).oppdatering(
                params = defaultParams(søknadsbehandling).plus(
                    "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering)
                ),
                session = session
            )
        }
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Iverksatt) {
        when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                dataSource.withSession { session ->
                    (
                        """
                       update behandling set status = :status, attestering = to_json(:attestering::json), utbetalingId = :utbetalingId, iverksattJournalpostId = :iverksattJournalpostId, iverksattBrevbestillingId = :iverksattBrevbestillingId  where id = :id
                        """.trimIndent()
                        ).oppdatering(
                        params = defaultParams(søknadsbehandling).plus(
                            listOf(
                                "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering),
                                "utbetalingId" to søknadsbehandling.utbetalingId,
                                "iverksattBrevbestillingId" to EksterneIverksettingsstegEtterUtbetalingMapper.iverksattBrevbestillingId(søknadsbehandling.eksterneIverksettingsteg)?.toString(),
                                "iverksattJournalpostId" to EksterneIverksettingsstegEtterUtbetalingMapper.iverksattJournalpostId(søknadsbehandling.eksterneIverksettingsteg)?.toString(),
                            )
                        ),
                        session = session
                    )
                }
            }
            is Søknadsbehandling.Iverksatt.Avslag -> {
                dataSource.withSession { session ->
                    (
                        """
                       update behandling set status = :status, attestering = to_json(:attestering::json), iverksattJournalpostId = :iverksattJournalpostId, iverksattBrevbestillingId = :iverksattBrevbestillingId  where id = :id
                        """.trimIndent()
                        ).oppdatering(
                        params = defaultParams(søknadsbehandling).plus(
                            listOf(
                                "attestering" to objectMapper.writeValueAsString(søknadsbehandling.attestering),
                                "iverksattBrevbestillingId" to EksterneIverksettingsstegForAvslagMapper.iverksattBrevbestillingId(søknadsbehandling.eksterneIverksettingsteg)?.toString(),
                                "iverksattJournalpostId" to EksterneIverksettingsstegForAvslagMapper.iverksattJournalpostId(søknadsbehandling.eksterneIverksettingsteg)?.toString(),
                            )
                        ),
                        session = session
                    )
                }
            }
        }
    }

    private fun defaultParams(søknadsbehandling: Søknadsbehandling): Map<String, Any> {
        return mapOf(
            "id" to søknadsbehandling.id,
            "sakId" to søknadsbehandling.sakId,
            "soknadId" to søknadsbehandling.søknad.id,
            "opprettet" to søknadsbehandling.opprettet,
            "status" to søknadsbehandling.status.name,
            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
        )
    }
}
