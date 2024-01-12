package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.domain.grunnlag.Bosituasjon
import java.util.UUID

internal class BosituasjongrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    private enum class Bosituasjonstype {
        ALENE,
        MED_VOKSNE,
        EPS_67_ELLER_ELDRE,
        EPS_UNDER_67,
        EPS_UNDER_67_UFØR_FLYKTNING,
        HAR_IKKE_EPS,
        HAR_IKKE_VALGT_UFØR_FLYKTNING,
    }

    private fun Row.toBosituasjongrunnlag(): Bosituasjon {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed"))
        val epsFnr = stringOrNull("eps_fnr")?.let {
            Fnr(it)
        }

        return when (Bosituasjonstype.valueOf(string("bosituasjontype"))) {
            Bosituasjonstype.ALENE -> Bosituasjon.Fullstendig.Enslig(
                id = id,
                opprettet = opprettet,
                periode = periode,
            )
            Bosituasjonstype.MED_VOKSNE -> Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = id,
                opprettet = opprettet,
                periode = periode,
            )
            Bosituasjonstype.EPS_67_ELLER_ELDRE -> Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
            Bosituasjonstype.EPS_UNDER_67 -> Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
            Bosituasjonstype.EPS_UNDER_67_UFØR_FLYKTNING -> Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
            Bosituasjonstype.HAR_IKKE_EPS -> Bosituasjon.Ufullstendig.HarIkkeEps(
                id = id,
                opprettet = opprettet,
                periode = periode,
            )
            Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING -> Bosituasjon.Ufullstendig.HarEps(
                id = id,
                opprettet = opprettet,
                periode = periode,
                fnr = epsFnr!!,
            )
        }
    }

    internal fun lagreBosituasjongrunnlag(
        behandlingId: UUID,
        grunnlag: List<Bosituasjon>,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreBosituasjongrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            grunnlag.forEach { bosituasjon ->
                lagre(behandlingId, bosituasjon, tx)
            }
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

    private fun lagre(behandlingId: UUID, grunnlag: Bosituasjon, tx: TransactionalSession) {
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
                        is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Bosituasjonstype.MED_VOKSNE
                        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> Bosituasjonstype.EPS_67_ELLER_ELDRE
                        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Bosituasjonstype.EPS_UNDER_67
                        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> Bosituasjonstype.EPS_UNDER_67_UFØR_FLYKTNING
                        is Bosituasjon.Fullstendig.Enslig -> Bosituasjonstype.ALENE
                        is Bosituasjon.Ufullstendig.HarIkkeEps -> Bosituasjonstype.HAR_IKKE_EPS
                        is Bosituasjon.Ufullstendig.HarEps -> Bosituasjonstype.HAR_IKKE_VALGT_UFØR_FLYKTNING
                    }.toString(),
                    "eps_fnr" to when (grunnlag) {
                        is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> null
                        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> grunnlag.fnr
                        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> grunnlag.fnr
                        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> grunnlag.fnr
                        is Bosituasjon.Fullstendig.Enslig -> null
                        is Bosituasjon.Ufullstendig.HarIkkeEps -> null
                        is Bosituasjon.Ufullstendig.HarEps -> grunnlag.fnr
                    },
                ),
                tx,
            )
    }

    internal fun hentBosituasjongrunnlag(behandlingId: UUID, session: Session): List<Bosituasjon> {
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
