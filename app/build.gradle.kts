plugins {
    id("com.android.application") version "8.13.2"
}

// CI signs with the release key from GitHub secrets; local builds without it fall back to the debug key.
val releaseKeystore: String? = System.getenv("BIRDY_KEYSTORE")

// "v1.2" tag builds become "1.2", "v1.3-beta.1" becomes "1.3-beta.1"; branch and local builds are "dev".
val appVersion: String = System.getenv("GITHUB_REF_NAME")?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }?.removePrefix("v") ?: "dev"

android {
    namespace = "dev.birdy.minimal"
    compileSdk = 36

    defaultConfig {
        applicationId = "birdy.minimal" // fixed by the Play Console listing; the code namespace above stays
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1 // grows with every CI build, so updates install
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("BIRDY_KEYSTORE_PASSWORD")
            keyAlias = "birdy"
            keyPassword = storePassword // PKCS12 keystores use one password for both
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Robolectric runs MainActivity on the JVM and needs the merged resources for that.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1") // tests only, nothing of it ships in the APK
}

// Full assertion messages in the CI log, not just the line number.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// "Neu in Birdy" after an update: this version's CHANGELOG section (only Neu / Verbessert / Behoben, as plain
// text) goes into the app as assets/whatsnew.txt. A beta without its own section shows "Unveröffentlicht";
// dev builds get an empty file and show nothing.
abstract class WhatsNewTask : DefaultTask() {
    @get:InputFile abstract val changelog: RegularFileProperty
    @get:Input abstract val version: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val text = changelog.get().asFile.readText()
        fun section(heading: String): String? {
            val start = Regex("^## " + Regex.escape(heading) + "\\s*$", RegexOption.MULTILINE).find(text) ?: return null
            val from = start.range.last + 1
            val next = Regex("^## ", RegexOption.MULTILINE).find(text, from)
            return text.substring(from, next?.range?.first ?: text.length)
        }
        val v = version.get()
        val body = when {
            v == "dev" -> ""
            else -> section(v) ?: (if (v.contains("-")) section("Unveröffentlicht") else null) ?: ""
        }
        val keep = setOf("Neu", "Verbessert", "Behoben")
        val out = StringBuilder()
        var current = ""
        for (line in body.lines()) {
            val heading = Regex("^### (.+)$").find(line)
            when {
                heading != null -> {
                    current = heading.groupValues[1].trim()
                    if (current in keep) out.append(if (out.isEmpty()) "" else "\n").append(current).append("\n")
                }
                current !in keep -> {}
                line.startsWith("- ") -> out.append("• ").append(line.removePrefix("- ").replace("**", "").replace("`", "")).append("\n")
                line.isNotBlank() -> out.append(line.trim().replace("**", "").replace("`", "")).append("\n")
            }
        }
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "whatsnew.txt").writeText(out.toString().trim())
    }
}

val whatsNew = tasks.register<WhatsNewTask>("whatsNew") {
    changelog.set(rootProject.file("CHANGELOG.md"))
    version.set(appVersion)
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(whatsNew, WhatsNewTask::outputDir) }
}
