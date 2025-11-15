import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.nio.file.Paths


val RCLONE_MODULE = "github.com/rclone/rclone"
val RCLONE_CUSTOM_VERSION_SUFFIX = "-extract"

val PROJECT_DIR = projectDir.absolutePath
val CACHE_PATH = Paths.get(PROJECT_DIR, "build/cache").toString()
val GOPATH = Paths.get(CACHE_PATH, "gopath").toString()
val OUTPUT_BASE_PATH = Paths.get(rootProject.projectDir.absolutePath, "app/src/main/jniLibs").toAbsolutePath().toString()
val GO = "/usr/local/go/bin/go"


val NDK_TOOLCHAIN = libs.versions.ndkToolchain.get()
val NDK = libs.versions.ndk.get()

fun findSdkDir(): String {
    val androidHome = System.getenv("ANDROID_HOME")
    if (androidHome != null) {
        return androidHome
    }

    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val properties = java.util.Properties()
        properties.load(localPropertiesFile.inputStream())
        val sdkDir = properties.getProperty("sdk.dir")
        if (sdkDir != null) {
            return sdkDir
        }
    }

    throw GradleException("Couldn't locate your Android SDK. Make sure you set sdk.dir in local.properties at the project root or set ANDROID_HOME.")
}

fun findNdkDir(): String {
    val sdkDir = findSdkDir()

    val ndkPath = Paths.get(sdkDir, "ndk", NDK).toAbsolutePath()
    if (!ndkPath.toFile().exists()) {

        // Try to install the NDK
        var sdkManagerPath = Paths.get(
            sdkDir,
            "cmdline-tools",
            "latest",
            "bin",
            "sdkmanager"
        ).toString()

        if (System.getProperty("os.name").startsWith("Windows")) {
            sdkManagerPath += ".bat"
        }

        try {
            exec {
                commandLine(sdkManagerPath, "--install", "ndk;$NDK")
            }
        } catch (exc: Exception) {
            logger.error(exc.toString())

            throw GradleException(
                "Couldn't find an NDK bundle in ${ndkPath}. Ensure the correct version is installed via Android Studio's SDK Manager or run \"$sdkManagerPath --install 'ndk;$NDK'\"."
            )
        }
    }

    return ndkPath.toString()
}

fun getCrossCompiler(abi: String): Path {
    val osName = System.getProperty("os.name")
    val osArch = System.getProperty("os.arch")

    val os = when {
        osName.startsWith("Windows") && osArch == "amd64" -> "windows-x86_64"
        osName.startsWith("Linux") && osArch == "amd64" -> "linux-x86_64"
        else -> null
    }

    if (os == null) {
        throw GradleException("Unsupported host OS or architecture.")
    }

    val abiToCompiler = mapOf(
        "armeabi-v7a" to "armv7a-linux-androideabi${NDK_TOOLCHAIN}-clang",
        "arm64-v8a" to "aarch64-linux-android${NDK_TOOLCHAIN}-clang",
        "x86" to "i686-linux-android${NDK_TOOLCHAIN}-clang",
        "x86_64" to "x86_64-linux-android${NDK_TOOLCHAIN}-clang"
    )

    return Paths.get(
        findNdkDir(),
        "toolchains",
        "llvm",
        "prebuilt",
        os,
        "bin",
        abiToCompiler.getValue(abi)
    )
}

fun getOutputPath(abi: String): String {
    return Paths.get(OUTPUT_BASE_PATH, abi, "librclone.so").toString()
}

tasks.register<Exec>("createDummyModule") {
    group = "rclone"
    // We create a dummy go module to be able to checkout our specific rclone
    // version later on.
    onlyIf {
        !Paths.get(CACHE_PATH, "go.mod").toFile().exists()
    }
    Paths.get(CACHE_PATH).toFile().mkdirs()

    workingDir(CACHE_PATH)
    environment("GOPATH", GOPATH)
    commandLine(GO, "mod", "init", "rclone")

}

tasks.register<Exec>("checkout") {
    group = "rclone"
    dependsOn("createDummyModule")
    workingDir(CACHE_PATH)
    environment("GOPATH", GOPATH)


    // Capture output of `go version`
    val goVersionOutput = ByteArrayOutputStream()

    project.exec {
        commandLine(GO, "version")
        standardOutput = goVersionOutput
    }

    if (goVersionOutput.toString().contains(libs.versions.go.get())) {
        println("> You are running the required go version.")
    } else {
        logger.error("> The required go version is: ${libs.versions.go.get()}")
        logger.error("> You are running: $goVersionOutput")
    }

    println("> You are checking out rclone v${libs.versions.rclone.get()}")

    commandLine(
        GO,
        "get",
        "-v",
        "-d",
        "$RCLONE_MODULE@v${libs.versions.rclone.get()}"
    )
}

fun buildRclone(abi: String): Task.() -> Unit {
    val abiToEnv = mapOf(
        "armeabi-v7a" to mapOf(
            "GOARCH" to "arm",
            "GOARM" to "7"
        ),
        "arm64-v8a" to mapOf("GOARCH" to "arm64"),
        "x86" to mapOf("GOARCH" to "386"),
        "x86_64" to mapOf("GOARCH" to "amd64")
    )

    return {
        group = "rclone"
        description = "Builds Rclone for $abi"

        doLast {
            println("> Building rclone for: $abi")

            exec {
                environment("GOPATH", GOPATH)
                val crossCompiler = getCrossCompiler(abi)

                environment("CC", crossCompiler)
                environment("CC_FOR_TARGET", crossCompiler)
                environment("GOOS", "android")
                environment("CGO_ENABLED", "1")
                environment("CGO_LDFLAGS", "-fuse-ld=lld -Wl,--hash-style=both -s")

                abiToEnv[abi]!!.forEach { (k, v) -> environment(k, v) }

                workingDir = File(CACHE_PATH)
                val rcloneVer = "${libs.versions.rclone.get()}$RCLONE_CUSTOM_VERSION_SUFFIX"
                val ldflags = "-buildid= -X github.com/rclone/rclone/fs.Version=$rcloneVer"

                commandLine(
                    GO,
                    "build",
                    "-tags", "android noselfupdate",
                    "-trimpath",
                    "-ldflags", ldflags,
                    "-o", getOutputPath(abi),
                    RCLONE_MODULE
                )
            }
        }
    }
}

var abis = listOf("Armeabi-v7a", "Arm64-v8a", "x86", "x86_64")
abis.forEach { abi ->
    tasks.register("build${abi.replace('-', '_')}") {
        group = "rclone"
        dependsOn("checkout")
        buildRclone(abi.lowercase()).invoke(this)
    }
}

tasks.register("buildAll") {
    group = "rclone"
    description = "Builds rclone for all ABIs"
    dependsOn(abis.map { "build${it.replace('-', '_')}" })
}