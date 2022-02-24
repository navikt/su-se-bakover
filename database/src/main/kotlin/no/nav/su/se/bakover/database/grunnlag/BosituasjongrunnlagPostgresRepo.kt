package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.BosituasjongrunnlagRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID
import javax.sql.DataSource

internal class BosituasjongrunnlagPostgresRepo(
    private val dataSource: DataSource,
    private val dbMetrics: DbMetrics,
) : BosituasjongrunnlagRepo {

    private enum class Bosituasjonstype {
        ALENE,
        MED_VOKSNE,
        EPS_67_ELLER_ELDRE,
        EPS_UNDER_67,
        EPS_UNDER_67_UFØR_FLYKTNING,
        HAR_IKKE_EPS,
        HAR_IKKE_VALGT_UFØR_FLYKTNING,
    }

    private fun Row.toBosituasjongrunnlag(): Grunnlag.Bosituasjon {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed"))
        val epsFnr = stringOrNull("eps_fnr")?.let {
            Fnr(it)
        }
        val begrunnelse = stringOrNull("begrunnelse")

        return when (Bosituasjonstype.valueOf(string("bosituasjontype"))) {
            Bosituasjonstype.ALENE -> Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = id, opprettet = opprettet, periode = periode, begrunnelse = begrunnelse,
            )
            Bosituasjonstype.MED_VOKSNE -> Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = id, opprettet = opprettet, periode = periode, begrunnelse = begrunnelse,
            )
            Bosituasjonstype.EPS_67_ELLER_ELDRE -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = id, opprettet = opprettet, periode = periode, fnr = epsFnr!!, begrunnelse = begrunnelse,
            )
            Bosituasjonstype.EPS_UNDER_67 -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
                begrunnelse = begrunnelse,
            )
            Bosituasjonstype.EPS_UNDER_67_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
                begrunnelse = begrunnelse,
            )
            Bosituasjonstype.HAR_IKKE_EPS -> Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = id,
                opprettet = opprettet,
                periode = periode,
            )
            Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.Ufullstendig.HarEps(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
        }
    }

    override fun lagreBosituasjongrunnlag(behandlingId: UUID, grunnlag: List<Grunnlag.Bosituasjon>) {
        dataSource.withTransaction { tx ->
            lagreBosituasjongrunnlag(
                behandlingId = behandlingId,
                grunnlag = grunnlag,
                tx = tx,
            )
        }
    }

    internal fun lagreBosituasjongrunnlag(behandlingId: UUID, grunnlag: List<Grunnlag.Bosituasjon>, tx: TransactionalSession) {
        slettForBehandlingId(behandlingId, tx)
        grunnlag.forEach { bosituasjon ->
            lagre(behandlingId, bosituasjon, tx)
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_bosituasjon where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun lagre(behandlingId: UUID, grunnlag: Grunnlag.Bosituasjon, tx: TransactionalSession) {
        """
            insert into grunnlag_bosituasjon
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                bosituasjontype,
                eps_fnr,
                begrunnelse
            ) values 
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                :bosituasjontype, 
                :eps_fnr,
                :begrunnelse
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to grunnlag.id,
                    "opprettet" to grunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to grunnlag.periode.fraOgMed,
                    "tilOgMed" to grunnlag.periode.tilOgMed,
                    "bosituasjontype" to when (grunnlag) {
                        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Bosituasjonstype.MED_VOKSNE
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> Bosituasjonstype.EPS_67_ELLER_ELDRE
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Bosituasjonstype.EPS_UNDER_67
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> Bosituasjonstype.EPS_UNDER_67_UFØR_FLYKTNING
                        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> Bosituasjonstype.ALENE
                        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps -> Bosituasjonstype.HAR_IKKE_EPS
                        is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING
                    }.toString(),
                    "eps_fnr" to when (grunnlag) {
                        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> null
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> null
                        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps -> null
                        is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> grunnlag.fnr
                    },
                    "begrunnelse" to when (grunnlag) {
                        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> null
                        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps -> null
                    },
                ),
                tx,
            )
    }

    override fun hentBosituasjongrunnlag(behandlingId: UUID): List<Grunnlag.Bosituasjon> {
        return dataSource.withSession { session ->
            hentBosituasjongrunnlag(behandlingId, session)
        }
    }

    internal fun hentBosituasjongrunnlag(behandlingId: UUID, session: Session): List<Grunnlag.Bosituasjon> {
        return dbMetrics.timeQuery("hentBosituasjonsgrunnlag") {
            """ select * from grunnlag_bosituasjon where behandlingid=:behandlingid""".trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingid" to behandlingId,
                    ),
                    session,
                ) {
                    it.toBosituasjongrunnlag()
                }
        }
    }
}
