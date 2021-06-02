package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID
import javax.sql.DataSource

class BosituasjongrunnlangPostgresRepo(
    private val dataSource: DataSource,
) : BosituasjongrunnlagRepo {

    private enum class Bosituasjonstype {
        ALENE,
        MED_VOKSNE,
        EPS_67_ELLER_ELDRE,
        EPS_UNDER_67,
        EPS_UNDER_67_UFØR_FLYKTNING,
        HAR_IKKE_VALGT_EPS,
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
            Bosituasjonstype.HAR_IKKE_VALGT_EPS -> Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEPS(
                id = id,
                opprettet = opprettet,
                periode = periode,
            )
            Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
        }
    }

    override fun lagreBosituasjongrunnlag(behandlingId: UUID, grunnlag: List<Grunnlag.Bosituasjon>) {
        dataSource.withTransaction { tx ->
            slettForBehandlingId(behandlingId, tx)
            dataSource.withSession { session ->
                grunnlag.forEach { bosituasjon ->
                    lagre(behandlingId, bosituasjon, session)
                }
            }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, session: Session) {
        """
            delete from grunnlag_bosituasjon where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            )
    }

    private fun lagre(behandlingId: UUID, grunnlag: Grunnlag.Bosituasjon, session: Session) {
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
                        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEPS -> Bosituasjonstype.HAR_IKKE_VALGT_EPS
                        is Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning -> Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING
                    }.toString(),
                    "eps_fnr" to when (grunnlag) {
                        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> null
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> null
                        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEPS -> null
                        is Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning -> grunnlag.fnr
                    },
                    "begrunnelse" to when (grunnlag) {
                        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> grunnlag.begrunnelse
                        is Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning -> null
                        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEPS -> null
                    }
                ),
                session,
            )
    }

    override fun hentBosituasjongrunnlag(behandlingId: UUID): List<Grunnlag.Bosituasjon> {
        return dataSource.withSession { session ->
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
