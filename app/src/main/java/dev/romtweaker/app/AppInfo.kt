package dev.romtweaker.app

/** Datos públicos de la app. El nombre del repositorio lo rellena el script de compilación a partir del remoto de git (sin datos personales). */
object AppInfo {
    const val REPO = "sebasbp466-dot/RomTweaker"
    val repoOk: Boolean get() = REPO.contains("/") && !REPO.startsWith("__")
    val repoUrl: String get() = if (repoOk) "https://github.com/" + REPO else ""
}
