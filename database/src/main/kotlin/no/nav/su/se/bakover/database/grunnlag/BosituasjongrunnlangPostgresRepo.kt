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

        return when (Bosituasjonstype.valueOf(string("bosituasjontype"))) {
            Bosituasjonstype.ALENE -> Grunnlag.Bosituasjon.Enslig(
                id = id, opprettet = opprettet, periode = periode,
            )
            Bosituasjonstype.MED_VOKSNE -> Grunnlag.Bosituasjon.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = id, opprettet = opprettet, periode = periode,
            )
            Bosituasjonstype.EPS_67_ELLER_ELDRE -> Grunnlag.Bosituasjon.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = id, opprettet = opprettet, periode = periode, fnr = epsFnr!!,
            )
            Bosituasjonstype.EPS_UNDER_67 -> Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
            Bosituasjonstype.EPS_UNDER_67_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
            Bosituasjonstype.HAR_IKKE_VALGT_EPS -> Grunnlag.Bosituasjon.HarIkkeEPS(
                id = id,
                opprettet = opprettet,
                periode = periode,
            )
            Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING -> Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeBestemt(
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
                eps_fnr
            ) values 
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                :bosituasjontype, 
                :eps_fnr
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
                        is Grunnlag.Bosituasjon.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Bosituasjonstype.MED_VOKSNE
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.SektiSyvEllerEldre -> Bosituasjonstype.EPS_67_ELLER_ELDRE
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Bosituasjonstype.EPS_UNDER_67
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.UførFlyktning -> Bosituasjonstype.EPS_UNDER_67_UFØR_FLYKTNING
                        is Grunnlag.Bosituasjon.Enslig -> Bosituasjonstype.ALENE
                        is Grunnlag.Bosituasjon.HarIkkeEPS -> Bosituasjonstype.HAR_IKKE_VALGT_EPS
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeBestemt -> Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING
                    }.toString(),
                    "eps_fnr" to when (grunnlag) {
                        is Grunnlag.Bosituasjon.DelerBoligMedVoksneBarnEllerAnnenVoksen -> null
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.SektiSyvEllerEldre -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.UførFlyktning -> grunnlag.fnr
                        is Grunnlag.Bosituasjon.Enslig -> null
                        is Grunnlag.Bosituasjon.HarIkkeEPS -> null
                        is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeBestemt -> grunnlag.fnr
                    },
                ),
                session,
            )
    }

    override fun hentBosituasjongrunnlag(behandlingId: UUID): List<Grunnlag.Bosituasjon> {
        return dataSource.withSession { session ->
            """ select * from grunnlag_bosituasjon where id=:id""".trimIndent()
                .hentListe(
                    mapOf(
                        "id" to behandlingId,
                    ),
                    session,
                ) {
                    it.toBosituasjongrunnlag()
                }
        }
    }
}
