buildscript {
    configurations.configureEach {
        resolutionStrategy.activateDependencyLocking()
        resolutionStrategy.eachDependency {
            val patched = mapOf(
                "org.apache.commons:commons-lang3" to "3.18.0",
                "org.apache.httpcomponents:httpclient" to "4.5.14",
                "org.bitbucket.b_c:jose4j" to "0.9.6",
                "org.jdom:jdom2" to "2.0.6.1"
            )
            val version = when {
                requested.group == "org.jetbrains.kotlin" -> "2.4.20"
                requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> "1.84"
                requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true -> "4.1.137.Final"
                else -> patched["${requested.group}:${requested.name}"]
            }
            if (version != null) { useVersion(version); because("Reviewed security patches; see security/SECURITY_REVIEW.md") }
        }
    }
}
plugins { id("com.android.application") version "9.3.2" apply false }
