package no.nav.su.se.bakover.database.skatt

import kotliquery.Row
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.database.common.YearRangeJson.Companion.toStringifiedYearRangeJson
import no.nav.su.se.bakover.database.skatt.SkattegrunnlagDbJson.Companion.toDbJson
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import java.util.UUID

internal data object SkattPostgresRepo {
    fun hent(id: UUID, session: Session): Skattegrunnlag? =
        "SELECT * FROM skatt WHERE id = :id".hent(mapOf("id" to id), session) {
            it.toSkattegrunnlag()
        }

    fun lagre(
        sakId: UUID,
        skatt: EksterneGrunnlagSkatt,
        session: Session,
    ) {
        return when (skatt) {
            is EksterneGrunnlagSkatt.Hentet -> {
                skatt.eps?.let { lagreForEps(sakId, it, session) }
                lagreForSøker(sakId, skatt.søkers, session)
            }

            EksterneGrunnlagSkatt.IkkeHentet -> Unit
        }
    }

    fun slettEksisterende(eksisterende: Skattereferanser, oppdterte: Skattereferanser, session: Session) {
        val skalSletteSøker = eksisterende.søkers != oppdterte.søkers
        val skalSletteEps = eksisterende.eps != null && eksisterende.eps != oppdterte.eps

        if (skalSletteSøker) {
            slettSkattegrunnlag(eksisterende.søkers, session)
        }
        if (skalSletteEps) {
            slettSkattegrunnlag(eksisterende.eps!!, session)
        }
    }

    private fun slettSkattegrunnlag(id: UUID, session: Session) {
        "delete from skatt where id=:id".oppdatering(mapOf("id" to id), session)
    }

    private fun lagreForSøker(sakId: UUID, skatt: Skattegrunnlag, session: Session) {
        lagreSkattekall(
            id = skatt.id, sakId = sakId,
            fnr = skatt.fnr, erEps = false,
            opprettet = skatt.hentetTidspunkt, data = skatt,
            saksbehandler = skatt.saksbehandler, årSpurtFor = skatt.årSpurtFor,
            session = session,
        )
    }

    private fun lagreForEps(sakId: UUID, skatt: Skattegrunnlag, session: Session) {
        lagreSkattekall(
            id = skatt.id, sakId = sakId,
            fnr = skatt.fnr, erEps = true,
            opprettet = skatt.hentetTidspunkt, data = skatt,
            saksbehandler = skatt.saksbehandler, årSpurtFor = skatt.årSpurtFor,
            session = session,
        )
    }

    private fun lagreSkattekall(
        id: UUID,
        sakId: UUID,
        fnr: Fnr,
        erEps: Boolean,
        opprettet: Tidspunkt,
        data: Skattegrunnlag?,
        saksbehandler: NavIdentBruker.Saksbehandler,
        årSpurtFor: YearRange,
        session: Session,
    ) {
        """
            insert into
                skatt (id, sakId, fnr, erEps, opprettet, saksbehandler, årSpurtFor, data)
            values
                (:id, :sakId, :fnr, :erEps, :opprettet, :saksbehandler, to_json(:aarSpurtFor::jsonb), to_json(:data::jsonb))
            on conflict (id) do update set
                opprettet=:opprettet, saksbehandler=:saksbehandler, årSpurtFor=to_json(:aarSpurtFor::jsonb), data=to_json(:data::jsonb)
        """.trimIndent().insert(
            mapOf(
                "id" to id,
                "sakId" to sakId,
                "fnr" to fnr.toString(),
                "erEps" to erEps,
                "opprettet" to opprettet,
                "saksbehandler" to saksbehandler,
                "aarSpurtFor" to årSpurtFor.toStringifiedYearRangeJson(),
                "data" to data?.toDbJson(),
            ),
            session = session,
        )
    }
}

fun Row.toSkattegrunnlag(): Skattegrunnlag {
    return SkattegrunnlagDbJson.toSkattegrunnlag(
        id = uuid("id"),
        årsgrunnlagJson = string("data"),
        fnr = string("fnr"),
        hentetTidspunkt = tidspunkt("opprettet"),
        saksbehandler = string("saksbehandler"),
        årSpurtFor = string("årSpurtFor"),
    )
}
