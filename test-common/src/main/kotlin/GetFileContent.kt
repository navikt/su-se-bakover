package no.nav.su.se.bakover.test

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Henter source filer fra test-common/src/main/kotlin/
 * @param path F.eks. simulering/reaktivering-delvis-tilbake-i-tid.xml eller kravgrunnlag/kravgrunnlag_endring.xml
 */
tailrec fun getFileSourceContent(
    path: String,
    currentRelativePath: Path = Paths.get("").toAbsolutePath(),
): String {
    // TODO jah: Var kanskje litt voldsomt for å slippe ha xml-filene spredt utover både kotlin og resources :shrug:
    if (!currentRelativePath.hasSubdirectoryNamed("test-common")) {
        return getFileSourceContent(path, currentRelativePath.parent)
    }
    return File(currentRelativePath.toFile(), "/test-common/src/main/kotlin/$path").readText()
}

private fun Path.hasSubdirectoryNamed(subdirectoryName: String): Boolean {
    if (!Files.isDirectory(this)) {
        throw IllegalArgumentException("Path $this is not a directory")
    }
    Files.newDirectoryStream(this).use { directoryStream ->
        for (file in directoryStream) {
            if (Files.isDirectory(file) && file.fileName.toString() == subdirectoryName) {
                return true
            }
        }
    }
    return false
}
