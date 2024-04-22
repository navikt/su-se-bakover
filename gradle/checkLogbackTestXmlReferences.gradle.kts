import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

val checkLogbackTestXmlReferences by tasks.registering {
    group = "verification"
    doLast {
        val sharedConfigFileLocation = File("${project.rootDir}/bootstrap/src/test/resources/logbackConfig-test.xml")
        if (!sharedConfigFileLocation.exists()) {
            throw IllegalStateException("Logback configuration file not found at ${sharedConfigFileLocation.absolutePath}")
        }

        val excludeDirs = setOf(".github", ".gradle", ".idea", "build", "build", "bootstrap")

        val logbackTestFiles = project.rootDir.walkTopDown()
            .onEnter { dir ->
                !excludeDirs.contains(dir.name)
            }
            .filter { it.isFile && it.name == "logback-test.xml" }
            .toList()

        if (logbackTestFiles.isEmpty()) {
            throw IllegalStateException("No 'logback-test.xml' configuration file found.")
        } else {
            logbackTestFiles.forEach { file ->
                val includeElement = getIncludeElement(file.absolutePath)
                val expectedPath = File(file.parentFile.parentFile.parentFile.parentFile, includeElement)
                if(!expectedPath.exists()) {
                    throw IllegalStateException("Illegal reference to non-existing file: $expectedPath in ${file.absolutePath}")
                }
            }
        }
        println("All logback-test.xml files are correctly referencing the shared logbackConfig-test.xml file.")
    }
}

private fun getIncludeElement(xmlFilePath: String): String {
    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val doc = dBuilder.parse(xmlFilePath)

    doc.documentElement.normalize()

    val include = doc.getElementsByTagName("include").item(0) ?: throw IllegalStateException("No 'include' element found in $xmlFilePath")
    return include.attributes?.getNamedItem("file")?.textContent ?: throw IllegalStateException("No 'file' attribute found in 'include' element in $xmlFilePath")
}

tasks.named("check") {
    dependsOn(checkLogbackTestXmlReferences)
}
