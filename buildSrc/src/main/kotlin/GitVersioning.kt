import org.gradle.api.Project
import java.io.ByteArrayOutputStream

// Every time you make a mistake in uploading the AppBundle to Google play console & can't reuse the version code,
// increate the below number.
private const val VERSION_BOOST = 2

/**
 * Get the version code from the count of git tags
 */
fun Project.getVersionCode(): Int {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine("git", "tag", "--list")
            standardOutput = stdout
        }
        stdout.toString().trim().lines().filter { it.isNotBlank() }.size + VERSION_BOOST
    } catch (e: Exception) {
        println("Warning: Could not get git tag count, using default version code -1")
        -1
    }
}

/**
 * Get the version name from git describe --tags --dirty
 */
fun Project.getVersionName(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine("git", "describe", "--tags", "--dirty")
            standardOutput = stdout
        }
        stdout.toString().trim()
    } catch (e: Exception) {
        println("Warning: Could not get git describe output, using default version name '1.0.0'")
        "1.0.0"
    }
}
