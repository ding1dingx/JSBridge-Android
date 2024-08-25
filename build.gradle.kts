import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.spotless) apply false
}

allprojects {
  plugins.apply("com.diffplug.spotless")
  extensions.configure<SpotlessExtension> {
    kotlin {
      target("src/**/*.kt")
      ktlint(
        rootProject.libs.ktlint.get().version,
      ).setEditorConfigPath(
        "${rootProject.rootDir}/.editorconfig",
      ).editorConfigOverride(
        mapOf(
          "android" to "true",
          "continuation_indent_size" to "2",
        ),
      )
    }

    kotlinGradle {
      ktlint(
        rootProject.libs.ktlint.get().version,
      ).setEditorConfigPath(
        "${rootProject.rootDir}/.editorconfig",
      )
    }

    format("xml") {
      target("src/**/*.xml")
      indentWithSpaces(2)
      trimTrailingWhitespace()
      endWithNewline()
      // Look for the first XML tag that isn't a comment (<!--) or the xml declaration (<?xml)
      licenseHeaderFile(rootProject.file("spotless/copyright.xml"), "(<[^!?])")
    }
  }
}

tasks.getByName("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}
