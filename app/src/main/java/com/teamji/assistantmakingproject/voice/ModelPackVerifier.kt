package com.teamji.assistantmakingproject.voice

import java.io.File
import java.security.MessageDigest

data class ModelFileRequirement(
    val relativePath: String,
    val sha256: String,
)

data class InstalledModelPack(
    val id: String,
    val rootDirectory: File,
    val files: List<ModelFileRequirement>,
)

data class ModelPackVerification(
    val isValid: Boolean,
    val errors: List<String>,
)

object ModelPackVerifier {
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun verify(pack: InstalledModelPack): ModelPackVerification {
        val root = pack.rootDirectory.canonicalFile
        val errors = buildList {
            if (!root.isDirectory) add("Model pack directory is missing: ${pack.id}")
            if (pack.files.isEmpty()) add("Model pack declares no files: ${pack.id}")

            pack.files.forEach { requirement ->
                if (!sha256Pattern.matches(requirement.sha256)) {
                    add("Invalid SHA-256 value for ${requirement.relativePath}")
                    return@forEach
                }

                val modelFile = runCatching { File(root, requirement.relativePath).canonicalFile }
                    .getOrElse {
                        add("Invalid model path: ${requirement.relativePath}")
                        return@forEach
                    }

                val isInsideRoot = modelFile.path == root.path ||
                    modelFile.path.startsWith(root.path + File.separator)
                if (!isInsideRoot) {
                    add("Model path escapes its pack directory: ${requirement.relativePath}")
                    return@forEach
                }
                if (!modelFile.isFile) {
                    add("Model file is missing: ${requirement.relativePath}")
                    return@forEach
                }

                val actualHash = modelFile.sha256()
                if (actualHash != requirement.sha256) {
                    add("Model checksum mismatch: ${requirement.relativePath}")
                }
            }
        }
        return ModelPackVerification(isValid = errors.isEmpty(), errors = errors)
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
