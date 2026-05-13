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
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.postgresql.util.PSQLException
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.sql.BatchUpdateException
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
            fnr = Fnr.generer(),
        )
        val opprettetOppgave = lagOpprettetOppgaveSaksresultat(
            sakId = UUID.randomUUID(),
            fnr = Fnr.generer(),
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
    fun `lagrer saksresultater i ny tabell for en kjøring`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val kjoringId = UUID.randomUUID()
        val annenKjoringId = UUID.randomUUID()
        val opprettet = Instant.parse("2026-01-15T08:01:00Z")
        val eksternFeil = lagEksternFeilSaksresultat(
            sakId = UUID.randomUUID(),
            fnr = Fnr.generer(),
        )
        val opprettetOppgave = lagOpprettetOppgaveSaksresultat(
            sakId = UUID.randomUUID(),
            fnr = Fnr.generer(),
        )
        val annenKjoringResultat = FradragssjekkSakResultat.IngenAvvik(
            sakId = UUID.randomUUID(),
            sakstype = Sakstype.ALDER,
            sjekkPunkter = listOf(
                lagSjekkpunkt(
                    fnr = Fnr.generer(),
                    fradragstype = Fradragstype.Alderspensjon,
                    ytelse = EksternYtelse.PESYS_ALDER,
                    lokaltBeløp = 2300.0,
                ),
            ),
        )

        repo.lagreSaksresultater(
            saker = listOf(eksternFeil, opprettetOppgave),
            måned = januar(2026),
            kjøringId = kjoringId,
            opprettet = opprettet,
        )
        repo.lagreSaksresultater(
            saker = listOf(annenKjoringResultat),
            måned = januar(2026),
            kjøringId = annenKjoringId,
            opprettet = opprettet.plusSeconds(60),
        )

        repo.hentSaksresultaterForKjoring(kjoringId) shouldContainExactlyInAnyOrder listOf(
            eksternFeil,
            opprettetOppgave,
        )

        helper.sessionFactory.withSession { session ->
            """
                select
                    kjoring_id,
                    sak_id,
                    dato,
                    opprettet,
                    status
                from fradragssjekk_resultat_per_kjoring
                where kjoring_id = :kjoringId
                order by sak_id
            """.trimIndent().hentListe(
                mapOf("kjoringId" to kjoringId),
                session,
            ) { row ->
                LagretSaksresultatPerKjoringRow(
                    kjoringId = row.uuid("kjoring_id"),
                    sakId = row.uuid("sak_id"),
                    dato = row.localDate("dato"),
                    opprettet = row.instant("opprettet"),
                    status = row.string("status"),
                )
            }
        } shouldContainExactlyInAnyOrder listOf(
            LagretSaksresultatPerKjoringRow(
                kjoringId = kjoringId,
                sakId = eksternFeil.sakId,
                dato = januar(2026).fraOgMed,
                opprettet = opprettet,
                status = FradragssjekkSakStatus.EKSTERN_FEIL.name,
            ),
            LagretSaksresultatPerKjoringRow(
                kjoringId = kjoringId,
                sakId = opprettetOppgave.sakId,
                dato = januar(2026).fraOgMed,
                opprettet = opprettet,
                status = FradragssjekkSakStatus.OPPGAVE_OPPRETTET.name,
            ),
        )
    }

    @Test
    fun `tillater ikke å sette inn samme primary key i resultat-tabellen to ganger`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val kjoringId = UUID.randomUUID()
        val opprettet = Instant.parse("2026-01-15T08:01:00Z")
        val saksresultat = lagOpprettetOppgaveSaksresultat(
            sakId = UUID.randomUUID(),
            fnr = Fnr.generer(),
        )

        repo.lagreSaksresultater(
            saker = listOf(saksresultat),
            måned = januar(2026),
            kjøringId = kjoringId,
            opprettet = opprettet,
        )

        val feil = assertFailsWith<BatchUpdateException> {
            repo.lagreSaksresultater(
                saker = listOf(saksresultat),
                måned = januar(2026),
                kjøringId = kjoringId,
                opprettet = opprettet.plusSeconds(60),
            )
        }

        (feil.nextException as PSQLException).sqlState shouldBe "23505"

        repo.hentSaksresultaterForKjoring(kjoringId) shouldBe listOf(saksresultat)

        helper.sessionFactory.withSession { session ->
            """
                select count(*) as count
                from fradragssjekk_resultat_per_kjoring
                where kjoring_id = :kjoringId
            """.trimIndent().antall(
                mapOf("kjoringId" to kjoringId),
                session,
            )
        } shouldBe 1L
    }

    @Test
    fun `henter sakIder med oppgave opprettet for måned`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val måned = januar(2026)
        val opprettet = Instant.parse("2026-01-15T08:01:00Z")
        val sakMedOppgave = UUID.randomUUID()
        val sakMedEksternFeil = UUID.randomUUID()
        val sakMedOppgaveIDryRun = UUID.randomUUID()
        val sakMedOppgaveIAnnenMåned = UUID.randomUUID()
        val sakMedOppgaveSomIkkeEtterspørres = UUID.randomUUID()

        val ordinærKjøring = lagKjoring(måned = måned, dryRun = false)
        repo.lagreKjoring(ordinærKjøring, lagFradragssjekkOppsummering(emptyList()))
        repo.lagreSaksresultater(
            saker = listOf(
                lagOpprettetOppgaveSaksresultat(sakId = sakMedOppgave, fnr = Fnr.generer()),
                lagEksternFeilSaksresultat(sakId = sakMedEksternFeil, fnr = Fnr.generer()),
                lagOpprettetOppgaveSaksresultat(sakId = sakMedOppgaveSomIkkeEtterspørres, fnr = Fnr.generer()),
            ),
            måned = måned,
            kjøringId = ordinærKjøring.id,
            opprettet = opprettet,
        )

        val dryRunKjøring = lagKjoring(måned = måned, dryRun = true, opprettet = opprettet.plusSeconds(60))
        repo.lagreKjoring(dryRunKjøring, lagFradragssjekkOppsummering(emptyList()))
        repo.lagreSaksresultater(
            saker = listOf(lagOpprettetOppgaveSaksresultat(sakId = sakMedOppgaveIDryRun, fnr = Fnr.generer())),
            måned = måned,
            kjøringId = dryRunKjøring.id,
            opprettet = opprettet.plusSeconds(60),
        )

        val annenMånedKjøring = lagKjoring(måned = februar(2026), dryRun = false, opprettet = opprettet.plusSeconds(120))
        repo.lagreKjoring(annenMånedKjøring, lagFradragssjekkOppsummering(emptyList()))
        repo.lagreSaksresultater(
            saker = listOf(lagOpprettetOppgaveSaksresultat(sakId = sakMedOppgaveIAnnenMåned, fnr = Fnr.generer())),
            måned = februar(2026),
            kjøringId = annenMånedKjøring.id,
            opprettet = opprettet.plusSeconds(120),
        )

        repo.hentSakIderMedOppgaveOpprettetForMåned(
            sakIder = listOf(
                sakMedOppgave,
                sakMedEksternFeil,
                sakMedOppgaveIDryRun,
                sakMedOppgaveIAnnenMåned,
            ),
            måned = måned,
        ) shouldBe setOf(sakMedOppgave)
    }

    @Test
    fun `lagrer ferdig aggregert oppsummering i databasen`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val forventetOppsummering = FradragssjekkOppsummering(
            nøkkeltall = mapOf(
                FradragssjekkSakStatus.OPPGAVE_OPPRETTET to 1,
            ),
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
    fun `henter kjøring med alle saksresultat-varianter`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val kjoringId = UUID.randomUUID()
        val opprettet = Instant.parse("2026-01-15T08:00:00Z")
        val ferdigstilt = Instant.parse("2026-01-15T08:05:00Z")
        val ingenAvvikSakId = UUID.randomUUID()
        val kunObservasjonSakId = UUID.randomUUID()
        val eksternFeilSakId = UUID.randomUUID()
        val oppgaveIkkeOpprettetDryRunSakId = UUID.randomUUID()
        val oppgaveOpprettetSakId = UUID.randomUUID()
        val oppgaveopprettelseFeiletSakId = UUID.randomUUID()
        val invariantbruddSakId = UUID.randomUUID()
        val ingenAvvikSjekkpunkt = lagSjekkpunkt(
            fnr = Fnr.generer(),
            fradragstype = Fradragstype.Alderspensjon,
            ytelse = EksternYtelse.PESYS_ALDER,
            lokaltBeløp = 1800.0,
        )
        val kunObservasjonSjekkpunkt = lagSjekkpunkt(
            fnr = Fnr.generer(),
            fradragstype = Fradragstype.Arbeidsavklaringspenger,
            ytelse = EksternYtelse.AAP,
            lokaltBeløp = 6400.0,
        )
        val eksternFeilSjekkpunkt = lagSjekkpunkt(
            fnr = Fnr.generer(),
            fradragstype = Fradragstype.Alderspensjon,
            ytelse = EksternYtelse.PESYS_ALDER,
            lokaltBeløp = 2200.0,
        )
        val oppgaveIkkeOpprettetDryRunSjekkpunkt = lagSjekkpunkt(
            fnr = Fnr.generer(),
            fradragstype = Fradragstype.Alderspensjon,
            ytelse = EksternYtelse.PESYS_ALDER,
            lokaltBeløp = 1500.0,
        )
        val oppgaveOpprettetSjekkpunkt = lagSjekkpunkt(
            fnr = Fnr.generer(),
            fradragstype = Fradragstype.Arbeidsavklaringspenger,
            ytelse = EksternYtelse.AAP,
            lokaltBeløp = 5100.0,
        )
        val oppgaveopprettelseFeiletSjekkpunkt = lagSjekkpunkt(
            fnr = Fnr.generer(),
            fradragstype = Fradragstype.Alderspensjon,
            ytelse = EksternYtelse.PESYS_ALDER,
            lokaltBeløp = 900.0,
        )
        val invariantbruddSjekkpunkt = lagSjekkpunkt(
            fnr = Fnr.generer(),
            fradragstype = Fradragstype.Arbeidsavklaringspenger,
            ytelse = EksternYtelse.AAP,
            lokaltBeløp = null,
        )
        val saksresultater = listOf(
            FradragssjekkSakResultat.IngenAvvik(
                sakId = ingenAvvikSakId,
                sakstype = Sakstype.ALDER,
                sjekkPunkter = listOf(ingenAvvikSjekkpunkt),
            ),
            FradragssjekkSakResultat.KunObservasjon(
                sakId = kunObservasjonSakId,
                sakstype = Sakstype.UFØRE,
                sjekkPunkter = listOf(kunObservasjonSjekkpunkt),
                observasjoner = listOf(lagObservasjon("Kun observasjon")),
            ),
            FradragssjekkSakResultat.EksternFeil(
                sakId = eksternFeilSakId,
                sakstype = Sakstype.ALDER,
                sjekkPunkter = listOf(eksternFeilSjekkpunkt),
                eksterneFeil = listOf(
                    EksternFeilPåSjekkpunkt(
                        sjekkpunkt = eksternFeilSjekkpunkt,
                        grunn = "Pesys svarte 500",
                    ),
                ),
            ),
            FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun(
                sakId = oppgaveIkkeOpprettetDryRunSakId,
                sakstype = Sakstype.ALDER,
                sjekkPunkter = listOf(oppgaveIkkeOpprettetDryRunSjekkpunkt),
                oppgaveGrunnlag = listOf(lagOppgaveGrunnlag("Dry-run oppgave")),
                observasjoner = listOf(lagObservasjon("Dry-run observasjon")),
            ),
            FradragssjekkSakResultat.OppgaveOpprettet(
                sakId = oppgaveOpprettetSakId,
                sakstype = Sakstype.UFØRE,
                sjekkPunkter = listOf(oppgaveOpprettetSjekkpunkt),
                oppgaveGrunnlag = listOf(lagOppgaveGrunnlag("Oppgave opprettet")),
                observasjoner = listOf(lagObservasjon("Har også observasjon")),
                opprettetOppgave = OppgaveopprettelseResultat.Opprettet(
                    oppgaveId = OppgaveId("54321"),
                    sakId = oppgaveOpprettetSakId,
                ),
            ),
            FradragssjekkSakResultat.OppgaveopprettelseFeilet(
                sakId = oppgaveopprettelseFeiletSakId,
                sakstype = Sakstype.ALDER,
                sjekkPunkter = listOf(oppgaveopprettelseFeiletSjekkpunkt),
                oppgaveGrunnlag = listOf(lagOppgaveGrunnlag("Oppgave feilet")),
                observasjoner = listOf(lagObservasjon("Observasjon ved feil")),
                mislykketOppgaveopprettelse = MislykketOppgaveopprettelse(
                    sakId = oppgaveopprettelseFeiletSakId,
                    avvikskoder = listOf(OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT),
                ),
            ),
            FradragssjekkSakResultat.Invariantbrudd(
                sakId = invariantbruddSakId,
                sakstype = Sakstype.UFØRE,
                sjekkPunkter = listOf(invariantbruddSjekkpunkt),
                feilmelding = "Mangler oppslagsresultat",
            ),
        )
        val kjoring = FradragssjekkKjøring(
            id = kjoringId,
            dato = januar(2026).fraOgMed,
            dryRun = false,
            status = FradragssjekkKjøringStatus.FULLFØRT,
            opprettet = opprettet,
            ferdigstilt = ferdigstilt,
        )

        repo.lagreKjoring(kjoring, lagFradragssjekkOppsummering(saksresultater))
        repo.lagreSaksresultater(saksresultater, januar(2026), kjoringId, opprettet)

        val hentet = checkNotNull(repo.hentKjoring(kjoringId))
        hentet.id shouldBe kjoring.id
        hentet.dato shouldBe kjoring.dato
        hentet.dryRun shouldBe kjoring.dryRun
        hentet.status shouldBe kjoring.status
        hentet.opprettet shouldBe kjoring.opprettet
        hentet.ferdigstilt shouldBe kjoring.ferdigstilt
        hentet.feilmelding shouldBe kjoring.feilmelding
        hentet.resultat.saksresultater shouldContainExactlyInAnyOrder saksresultater
    }

    @Test
    fun `henter feilet kjøring med feilmelding`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val kjoring = FradragssjekkKjøring(
            id = UUID.randomUUID(),
            dato = LocalDate.parse("2026-01-15"),
            dryRun = false,
            status = FradragssjekkKjøringStatus.FEILET,
            opprettet = Instant.parse("2026-01-15T08:00:00Z"),
            ferdigstilt = Instant.parse("2026-01-15T08:05:00Z"),
            feilmelding = "Noe gikk galt",
        )

        repo.lagreKjoring(kjoring, lagFradragssjekkOppsummering(emptyList()))

        repo.hentKjoring(kjoring.id) shouldBe kjoring.copy(resultat = FradragssjekkResultat())
    }

    @Test
    fun `tillater ikke flere ordinære kjøringer i samme år og måned`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val måned = januar(2026)

        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = false), lagFradragssjekkOppsummering(emptyList()))

        assertFailsWith<PSQLException> {
            repo.lagreKjoring(lagKjoring(måned = måned, dryRun = false), lagFradragssjekkOppsummering(emptyList()))
        }
    }

    @Test
    fun `tillater flere dry-runs i samme år og måned`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val måned = januar(2026)

        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = true), lagFradragssjekkOppsummering(emptyList()))
        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = true), lagFradragssjekkOppsummering(emptyList()))
    }

    @Test
    fun `tillater ordinære kjøringer i ulike måneder`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)

        repo.lagreKjoring(lagKjoring(måned = januar(2026), dryRun = false), lagFradragssjekkOppsummering(emptyList()))
        repo.lagreKjoring(lagKjoring(måned = februar(2026), dryRun = false), lagFradragssjekkOppsummering(emptyList()))
    }

    @Test
    fun `tillater dry-run og ordinær kjøring i samme år og måned`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)
        val måned = januar(2026)

        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = true), lagFradragssjekkOppsummering(emptyList()))
        repo.lagreKjoring(lagKjoring(måned = måned, dryRun = false), lagFradragssjekkOppsummering(emptyList()))
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
        val sjekkpunkt = lagSjekkpunkt(
            fnr = fnr,
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
                lagSjekkpunkt(
                    fnr = fnr,
                    fradragstype = Fradragstype.Alderspensjon,
                    ytelse = EksternYtelse.PESYS_ALDER,
                    lokaltBeløp = 1200.0,
                ),
            ),
            oppgaveGrunnlag = listOf(
                lagOppgaveGrunnlag("Bruker har avvik i alderpensjon"),
            ),
            observasjoner = listOf(
                lagObservasjon("Insignifikant differanse for samme sak"),
            ),
            opprettetOppgave = OppgaveopprettelseResultat.Opprettet(
                oppgaveId = OppgaveId("12345"),
                sakId = sakId,
            ),
        )
    }

    private fun lagSjekkpunkt(
        fnr: Fnr = Fnr.generer(),
        fradragstype: Fradragstype = Fradragstype.Alderspensjon,
        ytelse: EksternYtelse = EksternYtelse.PESYS_ALDER,
        lokaltBeløp: Double? = 1200.0,
        tilhører: FradragTilhører = FradragTilhører.BRUKER,
    ): Sjekkpunkt {
        return Sjekkpunkt(
            fnr = fnr,
            tilhører = tilhører,
            fradragstype = fradragstype,
            ytelse = ytelse,
            lokaltBeløp = lokaltBeløp,
        )
    }

    private fun lagOppgaveGrunnlag(
        oppgavetekst: String,
        fradragstype: FradragstypeData = FradragstypeData.fraDomain(Fradragstype.Alderspensjon),
    ): Fradragsfunn.Oppgavegrunnlag {
        return Fradragsfunn.Oppgavegrunnlag(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT,
            oppgavetekst = oppgavetekst,
            fradragstype = fradragstype,
        )
    }

    private fun lagObservasjon(loggtekst: String): Fradragsfunn.Observasjon {
        return Fradragsfunn.Observasjon(
            kode = Observasjonskode.INSIGNIFIKANT_BELOEPSDIFFERANSE,
            loggtekst = loggtekst,
        )
    }

    private data class LagretSaksresultatPerKjoringRow(
        val kjoringId: UUID,
        val sakId: UUID,
        val dato: LocalDate,
        val opprettet: Instant,
        val status: String,
    )
}
