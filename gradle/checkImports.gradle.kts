import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Hensikten med denne er å forsikre oss om at ikke :domain-moduler er avhengig av :infrastructure, :application eller :presentation.
 * Man kan og sjekke at :application ikke er avhengig av :presentation eller :infrastructure.
 * Tanken er at :infrastructure og :presentation ikke skal være direkte avhengig av hverandre, men at modulen som bootstrapper (den heter på dette tidspunktet application, men det er litt forvirrende) er avhengig av begge .
 */
val checkInfrastructureImports by tasks.registering {
    group = "verification"
    doLast {
        val dir = project.projectDir

        val grepOutput = ByteArrayOutputStream()
        val result: ExecResult = project.exec {
            commandLine("sh", "-c", "grep -rHn --include=*.kt 'import .*infrastructure.*' $dir | grep '/domain/'")
            standardOutput = grepOutput
            isIgnoreExitValue = true
        }

        val wrongImports = grepOutput.toString().trim().split("\n").mapNotNull { line ->
            if (line.isNotEmpty()) {
                val (filePath, lineNumber, offendingLine) = line.split(":", limit = 3)
                val urlEncodedPath = filePath.split("/")
                    .joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) }
                "file://$urlEncodedPath:$lineNumber\nOffending line $lineNumber: $offendingLine"
            } else null
        }

        if (wrongImports.isNotEmpty()) {
            throw GradleException("Domain modules should not depend on Infrastructure modules. Details:\n${wrongImports.joinToString("\n\n")}")
        }
    }
}

tasks.named("check") {
    dependsOn(checkInfrastructureImports)
}
