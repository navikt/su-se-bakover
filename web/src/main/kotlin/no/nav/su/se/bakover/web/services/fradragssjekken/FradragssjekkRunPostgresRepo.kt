package no.nav.su.se.bakover.web.services.fradragssjekken

import com.fasterxml.jackson.annotation.JsonInclude
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.Instant
import java.util.UUID

internal class FradragssjekkRunPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) {
    fun lagreKjoring(
        kjoring: FradragssjekkKjøring,
        oppsummering: FradragssjekkOppsummering,
    ) {
        sessionFactory.withTransaction { session ->
            """
                insert into fradragssjekk_kjoring (
                    id,
                    dato,
                    dry_run,
                    status,
                    opprettet,
                    ferdigstilt,
                    oppsummering,
                    feilmelding
                ) values (
                    :id,
                    :dato,
                    :dryRun,
                    :status,
                    :opprettet,
                    :ferdigstilt,
                    to_jsonb(:oppsummering::jsonb),
                    :feilmelding
                )
            """.trimIndent().insert(
                mapOf(
                    "id" to kjoring.id,
                    "dato" to kjoring.dato,
                    "dryRun" to kjoring.dryRun,
                    "status" to kjoring.status.name,
                    "opprettet" to kjoring.opprettet,
                    "ferdigstilt" to kjoring.ferdigstilt,
                    "oppsummering" to serialize(oppsummering),
                    "feilmelding" to kjoring.feilmelding,
                ),
                session,
            )
        }
    }

    fun hentKjoring(
        id: UUID,
    ): FradragssjekkKjøring? {
        return sessionFactory.withSession { session ->
            """
                select id, dato, dry_run, status, opprettet, ferdigstilt, feilmelding
                from fradragssjekk_kjoring
                where id = :id
            """.trimIndent().hent(
                mapOf("id" to id),
                session,
            ) { row ->
                row.tilFradragssjekkKjoring(
                    resultat = FradragssjekkResultat(
                        saksresultater = hentSaksresultaterForKjoring(id, session),
                    ),
                )
            }
        }
    }

    fun harOrdinaerKjoringForMåned(
        måned: Måned,
    ): Boolean {
        return sessionFactory.withSession { session ->
            """
                select 1
                from fradragssjekk_kjoring
                where extract(year from dato) = :year
                  and extract(month from dato) = :month
                  and dry_run = false
                limit 1
            """.trimIndent().hent(
                mapOf(
                    "year" to måned.fraOgMed.year,
                    "month" to måned.fraOgMed.monthValue,
                ),
                session,
            ) { true } == true
        }
    }

    fun hentSaksresultaterForKjoring(
        kjoringId: UUID,
    ): List<FradragssjekkSakResultat> {
        return sessionFactory.withSession { session ->
            hentSaksresultaterForKjoring(kjoringId, session)
        }
    }

    fun hentSaksresultaterMedEksternFeil(
        kjoringId: UUID,
    ): List<FradragssjekkSakResultat> {
        return hentSaksresultaterForKjoring(kjoringId)
            .filter { it.status == FradragssjekkSakStatus.EKSTERN_FEIL }
    }

    fun lagreSaksresultater(
        saker: List<FradragssjekkSakResultat>,
        måned: Måned,
        kjøringId: UUID,
        opprettet: Instant,
    ) {
        if (saker.isEmpty()) return

        val sql =
            """
                insert into fradragssjekk_resultat_per_kjoring (
                    kjoring_id,
                    sak_id,
                    dato,
                    opprettet,
                    resultat
                ) values (
                    :kjoringId,
                    :sakId,
                    :dato,
                    :opprettet,
                    to_jsonb(:resultat::jsonb)
                )
            """.trimIndent()

        sessionFactory.withTransaction { session ->
            val rows = saker.map { saksresultat ->
                mapOf(
                    "kjoringId" to kjøringId,
                    "sakId" to saksresultat.sakId,
                    "dato" to måned.fraOgMed,
                    "opprettet" to opprettet,
                    "resultat" to serialize(saksresultat.tilDbJson()),
                )
            }

            session.batchPreparedNamedStatement(sql, rows)
        }
    }

    private fun hentSaksresultaterForKjoring(
        kjoringId: UUID,
        session: Session,
    ): List<FradragssjekkSakResultat> {
        return """
            select resultat
            from fradragssjekk_resultat_per_kjoring
            where kjoring_id = :kjoringId
            order by sak_id
        """.trimIndent().hentListe(
            mapOf("kjoringId" to kjoringId),
            session,
        ) { row ->
            deserialize<FradragssjekkSakResultatDbJson>(row.string("resultat")).tilDomain()
        }
    }
}

private fun Row.tilFradragssjekkKjoring(
    resultat: FradragssjekkResultat,
): FradragssjekkKjøring {
    return FradragssjekkKjøring(
        id = uuid("id"),
        dato = localDate("dato"),
        dryRun = boolean("dry_run"),
        status = FradragssjekkKjøringStatus.valueOf(string("status")),
        opprettet = instant("opprettet"),
        ferdigstilt = instant("ferdigstilt"),
        resultat = resultat,
        feilmelding = stringOrNull("feilmelding"),
    )
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
private data class FradragssjekkSakResultatDbJson(
    val sakId: UUID,
    val sakstype: no.nav.su.se.bakover.common.domain.sak.Sakstype,
    val status: FradragssjekkSakStatus,
    val sjekkPunkter: List<SjekkpunktDbJson> = emptyList(),
    val oppgaveGrunnlag: List<Fradragsfunn.Oppgavegrunnlag> = emptyList(),
    val observasjoner: List<Fradragsfunn.Observasjon> = emptyList(),
    val opprettetOppgave: OppgaveopprettelseResultat.Opprettet? = null,
    val mislykketOppgaveopprettelse: MislykketOppgaveopprettelse? = null,
    val eksterneFeil: List<EksternFeilPåSjekkpunktDbJson> = emptyList(),
    val feilmelding: String? = null,
)

private data class SjekkpunktDbJson(
    val fnr: no.nav.su.se.bakover.common.person.Fnr,
    val tilhører: vilkår.inntekt.domain.grunnlag.FradragTilhører,
    val fradragstype: FradragstypeData,
    val ytelse: EksternYtelse,
    val lokaltBeløp: Double?,
)

private data class EksternFeilPåSjekkpunktDbJson(
    val sjekkpunkt: SjekkpunktDbJson,
    val grunn: String,
)

private fun FradragssjekkSakResultat.tilDbJson(): FradragssjekkSakResultatDbJson {
    return when (this) {
        is FradragssjekkSakResultat.IngenAvvik -> FradragssjekkSakResultatDbJson(
            sakId = sakId,
            sakstype = sakstype,
            status = status,
            sjekkPunkter = sjekkPunkter.map { it.tilDbJson() },
        )

        is FradragssjekkSakResultat.KunObservasjon -> FradragssjekkSakResultatDbJson(
            sakId = sakId,
            sakstype = sakstype,
            status = status,
            sjekkPunkter = sjekkPunkter.map { it.tilDbJson() },
            observasjoner = observasjoner,
        )

        is FradragssjekkSakResultat.EksternFeil -> FradragssjekkSakResultatDbJson(
            sakId = sakId,
            sakstype = sakstype,
            status = status,
            sjekkPunkter = sjekkPunkter.map { it.tilDbJson() },
            eksterneFeil = eksterneFeil.map { it.tilDbJson() },
        )

        is FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun -> FradragssjekkSakResultatDbJson(
            sakId = sakId,
            sakstype = sakstype,
            status = status,
            sjekkPunkter = sjekkPunkter.map { it.tilDbJson() },
            oppgaveGrunnlag = oppgaveGrunnlag,
            observasjoner = observasjoner,
        )

        is FradragssjekkSakResultat.OppgaveOpprettet -> FradragssjekkSakResultatDbJson(
            sakId = sakId,
            sakstype = sakstype,
            status = status,
            sjekkPunkter = sjekkPunkter.map { it.tilDbJson() },
            oppgaveGrunnlag = oppgaveGrunnlag,
            observasjoner = observasjoner,
            opprettetOppgave = opprettetOppgave,
        )

        is FradragssjekkSakResultat.OppgaveopprettelseFeilet -> FradragssjekkSakResultatDbJson(
            sakId = sakId,
            sakstype = sakstype,
            status = status,
            sjekkPunkter = sjekkPunkter.map { it.tilDbJson() },
            oppgaveGrunnlag = oppgaveGrunnlag,
            observasjoner = observasjoner,
            mislykketOppgaveopprettelse = mislykketOppgaveopprettelse,
        )

        is FradragssjekkSakResultat.Invariantbrudd -> FradragssjekkSakResultatDbJson(
            sakId = sakId,
            sakstype = sakstype,
            status = status,
            sjekkPunkter = sjekkPunkter.map { it.tilDbJson() },
            feilmelding = feilmelding,
        )
    }
}

private fun FradragssjekkSakResultatDbJson.tilDomain(): FradragssjekkSakResultat {
    return when (status) {
        FradragssjekkSakStatus.INGEN_AVVIK -> FradragssjekkSakResultat.IngenAvvik(
            sakId = sakId,
            sakstype = sakstype,
            sjekkPunkter = sjekkPunkter.map { it.tilDomain() },
        )

        FradragssjekkSakStatus.KUN_OBSERVASJON -> FradragssjekkSakResultat.KunObservasjon(
            sakId = sakId,
            sakstype = sakstype,
            sjekkPunkter = sjekkPunkter.map { it.tilDomain() },
            observasjoner = observasjoner,
        )

        FradragssjekkSakStatus.EKSTERN_FEIL -> FradragssjekkSakResultat.EksternFeil(
            sakId = sakId,
            sakstype = sakstype,
            sjekkPunkter = sjekkPunkter.map { it.tilDomain() },
            eksterneFeil = eksterneFeil.map { it.tilDomain() },
        )

        FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN -> FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun(
            sakId = sakId,
            sakstype = sakstype,
            sjekkPunkter = sjekkPunkter.map { it.tilDomain() },
            oppgaveGrunnlag = oppgaveGrunnlag,
            observasjoner = observasjoner,
        )

        FradragssjekkSakStatus.OPPGAVE_OPPRETTET -> FradragssjekkSakResultat.OppgaveOpprettet(
            sakId = sakId,
            sakstype = sakstype,
            sjekkPunkter = sjekkPunkter.map { it.tilDomain() },
            oppgaveGrunnlag = oppgaveGrunnlag,
            observasjoner = observasjoner,
            opprettetOppgave = requireNotNull(opprettetOppgave) { "Mangler opprettetOppgave for status=$status" },
        )

        FradragssjekkSakStatus.OPPGAVEOPPRETTELSE_FEILET -> FradragssjekkSakResultat.OppgaveopprettelseFeilet(
            sakId = sakId,
            sakstype = sakstype,
            sjekkPunkter = sjekkPunkter.map { it.tilDomain() },
            oppgaveGrunnlag = oppgaveGrunnlag,
            observasjoner = observasjoner,
            mislykketOppgaveopprettelse = requireNotNull(mislykketOppgaveopprettelse) { "Mangler mislykketOppgaveopprettelse for status=$status" },
        )

        FradragssjekkSakStatus.INVARIANTBRUDD -> FradragssjekkSakResultat.Invariantbrudd(
            sakId = sakId,
            sakstype = sakstype,
            sjekkPunkter = sjekkPunkter.map { it.tilDomain() },
            feilmelding = feilmelding,
        )
    }
}

private fun Sjekkpunkt.tilDbJson(): SjekkpunktDbJson {
    return SjekkpunktDbJson(
        fnr = fnr,
        tilhører = tilhører,
        fradragstype = FradragstypeData.fraDomain(fradragstype),
        ytelse = ytelse,
        lokaltBeløp = lokaltBeløp,
    )
}

private fun SjekkpunktDbJson.tilDomain(): Sjekkpunkt {
    return Sjekkpunkt(
        fnr = fnr,
        tilhører = tilhører,
        fradragstype = fradragstype.tilDomain(),
        ytelse = ytelse,
        lokaltBeløp = lokaltBeløp,
    )
}

private fun EksternFeilPåSjekkpunkt.tilDbJson(): EksternFeilPåSjekkpunktDbJson {
    return EksternFeilPåSjekkpunktDbJson(
        sjekkpunkt = sjekkpunkt.tilDbJson(),
        grunn = grunn,
    )
}

private fun EksternFeilPåSjekkpunktDbJson.tilDomain(): EksternFeilPåSjekkpunkt {
    return EksternFeilPåSjekkpunkt(
        sjekkpunkt = sjekkpunkt.tilDomain(),
        grunn = grunn,
    )
}
