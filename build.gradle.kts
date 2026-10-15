plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * Regla E Ink (apartado 5.4): ninguna animación en el código de la app.
 * Falla si encuentra en `src/` alguna API que anime. Se ejecuta con `check`.
 */
abstract class CheckNoAnimations : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val root: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    private val forbidden = listOf(
        Regex("""\banimate[A-Z]\w*\s*\(""") to "animate*",
        Regex("""\bAnimatedVisibility\b""") to "AnimatedVisibility",
        Regex("""\bAnimatedContent\b""") to "AnimatedContent",
        Regex("""\bCrossfade\b""") to "Crossfade",
        Regex("""\brememberRipple\b|\bripple\s*\(""") to "ripple",
        Regex("""\bupdateTransition\b|\brememberInfiniteTransition\b""") to "transition",
        Regex("""\b(Horizontal|Vertical)Pager\b""") to "Pager (desliza con animación)",
    )

    @TaskAction
    fun run() {
        val rootDir = root.get().asFile
        val hits = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().withIndex().flatMap { (i, line) ->
                val code = line.substringBefore("//")
                forbidden.filter { (regex, _) -> regex.containsMatchIn(code) }
                    .map { (_, name) -> "${file.relativeTo(rootDir).invariantSeparatorsPath}:${i + 1}: $name" }
            }
        }
        report.get().asFile.writeText(hits.joinToString("\n"))
        if (hits.isNotEmpty()) {
            throw GradleException(
                "APIs de animación prohibidas en E Ink (apartado 5.4):\n" + hits.joinToString("\n") { "  $it" }
            )
        }
    }
}

val checkNoAnimations = tasks.register<CheckNoAnimations>("checkNoAnimations") {
    group = "verification"
    description = "Prohíbe APIs de animación en el código fuente (regla E Ink)."
    sources.from(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "build-logic/**")
        }
    )
    root.set(layout.projectDirectory)
    report.set(layout.buildDirectory.file("reports/checkNoAnimations.txt"))
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkNoAnimations) }
}
