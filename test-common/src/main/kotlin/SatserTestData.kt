package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.tid.Tidspunkt
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import vilkår.formue.domain.FormuegrenserFactory
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// kommentar jah: Dersom/når satser flyttes ut av kildekoden, f.eks. til databasen, må vi definere satsene for test her. Kan starte med å kopiere [SatsFactoryForSupplerendeStønad]

val satsFactoryTest = SatsFactoryForSupplerendeStønad()

fun satsFactoryTestPåDato(påDato: LocalDate = fixedLocalDate) = satsFactoryTest.gjeldende(påDato = påDato)

fun formuegrenserFactoryTestPåDato(
    påDato: LocalDate = fixedLocalDate,
): FormuegrenserFactory {
    val satsFactoryTestPåDato = satsFactoryTestPåDato(påDato = påDato)
    return FormuegrenserFactory.createFromGrunnbeløp(
        grunnbeløpFactory = satsFactoryTestPåDato.grunnbeløpFactory,
        tidligsteTilgjengeligeMåned = satsFactoryTestPåDato.tidligsteTilgjengeligeMåned,
    )
}

fun formuegrenserFactoryTestPåDato(
    påDato: Tidspunkt = fixedTidspunkt,
    zoneId: ZoneId = ZoneOffset.UTC,
) = formuegrenserFactoryTestPåDato(påDato.toLocalDate(zoneId))
