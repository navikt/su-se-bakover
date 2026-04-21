package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.persistence.antall
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.postgresql.util.PSQLException
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertFailsWith

@ExtendWith(DbExtension::class)
internal class FradragssjekkRunPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter kjøring og saksresultater`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)

        val eksternFeil = lagEksternFeilSaksresultat(
            sakId = UUID.randomUUID(),
            fnr = Fnr("12345678901"),
        )
        val opprettetOppgave = lagOpprettetOppgaveSaksresultat(
            sakId = UUID.randomUUID(),
            fnr = Fnr("10987654321"),
        )

        val resultat = FradragssjekkResultat(
            saksresultater = listOf(eksternFeil, opprettetOppgave),
        )

        val kjoringId = UUID.randomUUID()
        val opprettet = Instant.parse("2026-01-15T08:00:00Z")
        val ferdigstilt = Instant.parse("2026-01-15T08:05:00Z")
        val resultatOpprettet = Instant.parse("2026-01-15T08:01:00Z")

        val fullfortKjoring = FradragssjekkKjøring(
            id = kjoringId,
            dato = LocalDate.parse("2026-01-15"),
            dryRun = true,
            status = FradragssjekkKjøringStatus.FULLFØRT,
            opprettet = opprettet,
            ferdigstilt = ferdigstilt,
        )

        repo.lagreKjoring(fullfortKjoring, lagFradragssjekkOppsummering(resultat.saksresultater))
        repo.lagreSaksresultater(resultat.saksresultater, januar(2026), kjoringId, resultatOpprettet)

        repo.hentSaksresultaterForKjoring(kjoringId) shouldContainExactlyInAnyOrder listOf(
            eksternFeil,
            opprettetOppgave,
        )

        repo.hentSaksresultaterMedEksternFeil(kjoringId) shouldBe listOf(eksternFeil)

        helper.sessionFactory.withSession { session ->
            """
                select count(*) as count
                from fradragssjekk_resultat_per_kjoring
                where kjoring_id = :kjoringId
            """.trimIndent().antall(
                mapOf("kjoringId" to kjoringId),
                session,
            )
        } shouldBe 2L

        helper.sessionFactory.withSession { session ->
            """
                select opprettet
                from fradragssjekk_resultat_per_kjoring
                where kjoring_id = :kjoringId
                order by sak_id
            """.trimIndent().hentListe(
                mapOf("kjoringId" to kjoringId),
                session,
            ) { row ->
                row.instant("opprettet")
            }
        } shouldBe listOf(resultatOpprettet, resultatOpprettet)
    }

    @Test
    fun `lagrer ferdig aggregert oppsummering i databasen`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val forventetOppsummering = FradragssjekkOppsummering(
            antallOppgaver = 1,
            oppgaverPerSakstype = listOf(
                FradragssjekkSakstypeStatistikk(
                    sakstype = Sakstype.ALDER,
                    antallOppgaver = 1,
                    oppgaverPerFradrag = listOf(
                        FradragssjekkFradragStatistikk(
                            fradragstype = "Alderspensjon",
                            beskrivelse = null,
                            antallOppgaver = 1,
                        ),
                    ),
                ),
            ),
        )
        val kjoring = FradragssjekkKjøring(
            id = UUID.randomUUID(),
            dato = LocalDate.parse("2026-01-15"),
            dryRun = false,
            status = FradragssjekkKjøringStatus.FULLFØRT,
            opprettet = Instant.parse("2026-01-15T08:00:00Z"),
            ferdigstilt = Instant.parse("2026-01-15T08:05:00Z"),
        )

        repo.lagreKjoring(kjoring, forventetOppsummering)

        helper.sessionFactory.withSession { session ->
            """
                select oppsummering
                from fradragssjekk_kjoring
                where id = :id
            """.trimIndent().hent(
                mapOf("id" to kjoring.id),
                session,
            ) { row ->
                deserialize<FradragssjekkOppsummering>(row.string("oppsummering"))
            }
        } shouldBe forventetOppsummering
    }

    @Test
    fun `tillater ikke flere ordinære kjøringer i samme år og måned`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val måned = januar(2026)

        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = false))

        assertFailsWith<PSQLException> {
            repo.lagreKjoring(lagKjoring(måned = måned, dryRun = false))
        }
    }

    @Test
    fun `tillater flere dry-runs i samme år og måned`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val måned = januar(2026)

        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = true))
        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = true))
    }

    @Test
    fun `tillater ordinære kjøringer i ulike måneder`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)

        repo.lagreKjoring(lagKjoring(måned = januar(2026), dryRun = false))
        repo.lagreKjoring(lagKjoring(måned = februar(2026), dryRun = false))
    }

    @Test
    fun `tillater dry-run og ordinær kjøring i samme år og måned`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val måned = januar(2026)

        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = true))
        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = false))
    }

    private fun lagKjoring(
        måned: Måned,
        dryRun: Boolean,
        opprettet: Instant = Instant.parse("2026-01-15T08:00:00Z"),
    ): FradragssjekkKjøring {
        return FradragssjekkKjøring(
            id = UUID.randomUUID(),
            dato = måned.fraOgMed,
            dryRun = dryRun,
            status = FradragssjekkKjøringStatus.FULLFØRT,
            opprettet = opprettet,
            ferdigstilt = Instant.parse("2026-01-15T08:05:00Z"),
        )
    }

    private fun lagEksternFeilSaksresultat(
        sakId: UUID,
        fnr: Fnr,
    ): FradragssjekkSakResultat {
        val sjekkpunkt = Sjekkpunkt(
            fnr = fnr,
            tilhører = FradragTilhører.BRUKER,
            fradragstype = Fradragstype.Arbeidsavklaringspenger,
            ytelse = EksternYtelse.AAP,
            lokaltBeløp = 5000.0,
        )

        return FradragssjekkSakResultat.EksternFeil(
            sakId = sakId,
            sakstype = Sakstype.ALDER,
            sjekkPunkter = listOf(sjekkpunkt),
            eksterneFeil = listOf(
                EksternFeilPåSjekkpunkt(
                    sjekkpunkt = sjekkpunkt,
                    grunn = "Feil ved oppslag",
                ),
            ),
        )
    }

    private fun lagOpprettetOppgaveSaksresultat(
        sakId: UUID,
        fnr: Fnr,
    ): FradragssjekkSakResultat {
        return FradragssjekkSakResultat.OppgaveOpprettet(
            sakId = sakId,
            sakstype = Sakstype.ALDER,
            sjekkPunkter = listOf(
                Sjekkpunkt(
                    fnr = fnr,
                    tilhører = FradragTilhører.BRUKER,
                    fradragstype = Fradragstype.Alderspensjon,
                    ytelse = EksternYtelse.PESYS_ALDER,
                    lokaltBeløp = 1200.0,
                ),
            ),
            oppgaveAvvik = listOf(
                Fradragsfunn.Oppgaveavvik(
                    kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT,
                    oppgavetekst = "Bruker har avvik i alderpensjon",
                ),
            ),
            observasjoner = listOf(
                Fradragsfunn.Observasjon(
                    kode = Observasjonskode.INSIGNIFIKANT_BELOEPSDIFFERANSE,
                    loggtekst = "Insignifikant differanse for samme sak",
                ),
            ),
            opprettetOppgave = OppgaveopprettelseResultat.Opprettet(
                oppgaveId = OppgaveId("12345"),
                sakId = sakId,
            ),
        )
    }
}
