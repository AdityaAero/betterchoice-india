plugins { id("com.android.application") }
val robolectricSdk by configurations.creating { isCanBeConsumed = false }
android {
    namespace = "com.betterchoice.android"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        // Separate package so "BetterChoice India" installs alongside other BetterChoice test builds.
        applicationId = "com.betterchoice.india"
        minSdk = 31
        targetSdk = 37
        versionCode = 53
        versionName = "3.9.0-personal-card"
        // Bundled on-device text recognition ships native code: 64-bit ARM phones, 32-bit ARM phones (budget and
        // Android Go handsets, which run only 32-bit apps) and the x86_64 emulator. Play delivers each phone only
        // its own ABI from the App Bundle; a sideloaded APK carries all three.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // Public project configuration only. Service-role/SMTP/SMS secrets never belong here.
        fun publicConfig(name: String): String = providers.environmentVariable(name).orElse("").get()
        fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""
        buildConfigField("String", "AUTH_URL", quoted(publicConfig("BC_SUPABASE_URL")))
        val publicKey = publicConfig("BC_SUPABASE_PUBLISHABLE_KEY")
        require(publicKey.isEmpty() || publicKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]{20,}"))) {
            "BC_SUPABASE_PUBLISHABLE_KEY must be a publishable key. Never supply server secrets."
        }
        buildConfigField("String", "AUTH_PUBLIC_KEY", quoted(publicKey))
        buildConfigField("String", "CAPTCHA_URL", quoted(publicConfig("BC_CAPTCHA_URL")))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    // Keep local test upgrades compatible when ANDROID_USER_HOME changes between builds.
    // This optional key is for DEBUG only; release signing remains entirely separate.
    providers.environmentVariable("BC_DEBUG_KEYSTORE").orNull?.let { debugKey ->
        signingConfigs.getByName("debug") { storeFile = file(debugKey) }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Debug diagnostics call android.util.Log; plain JUnit tests should get a no-op, not a crash.
        unitTests.isReturnDefaultValues = true
    }
    val signingValues = listOf("BC_KEYSTORE_PATH", "BC_KEYSTORE_PASSWORD", "BC_KEY_ALIAS", "BC_KEY_PASSWORD")
        .map { providers.environmentVariable(it).orNull }
    require(signingValues.all { it == null } || signingValues.all { !it.isNullOrBlank() }) {
        "Provide all four BC signing environment variables or none."
    }
    if (signingValues.all { !it.isNullOrBlank() }) {
        signingConfigs.create("production") {
            storeFile = file(signingValues[0]!!)
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.findByName("production") != null) signingConfig = signingConfigs.getByName("production")
        }
    }
}
dependencyLocking { lockAllConfigurations() }
// Patch isolated lint/test tool configurations too; they never become runtime dependencies.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        val patched = mapOf(
            "org.apache.commons:commons-lang3" to "3.18.0",
            "org.apache.httpcomponents:httpclient" to "4.5.14",
            "org.bitbucket.b_c:jose4j" to "0.9.6",
            "org.jdom:jdom2" to "2.0.6.1"
        )
        val version = when {
            requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> "1.84"
            requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true -> "4.1.137.Final"
            else -> patched["${requested.group}:${requested.name}"]
        }
        if (version != null) { useVersion(version); because("Reviewed security patches; see security/SECURITY_REVIEW.md") }
    }
}
dependencies {
    // On-device text recognition for pack photos (bundled Latin model; no model download at runtime).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // On-device barcode scanning: reads the EAN/UPC number printed on the pack, bundled the same way.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    add(robolectricSdk.name, "org.robolectric:android-all-instrumented:12-robolectric-7732740-i7")
}
val prepareRobolectricSdk by tasks.registering(Sync::class) {
    from(robolectricSdk)
    into(layout.buildDirectory.dir("robolectric-sdk"))
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricSdk)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath)
}
