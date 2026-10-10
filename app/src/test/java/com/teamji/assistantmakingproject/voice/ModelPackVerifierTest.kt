package com.teamji.assistantmakingproject.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ModelPackVerifierTest {
    @Test
    fun `accepts model file with matching checksum`() {
        val root = createTempDir(prefix = "model_pack_")
        try {
            val model = File(root, "voice.onnx").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val result = ModelPackVerifier.verify(
                InstalledModelPack(
                    id = "test-pack",
                    rootDirectory = root,
                    files = listOf(ModelFileRequirement("voice.onnx", model.sha256())),
                )
            )
            assertTrue(result.errors.joinToString(), result.isValid)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `rejects path traversal outside model directory`() {
        val root = createTempDir(prefix = "model_pack_")
        val outside = File(root.parentFile, "${root.name}_outside_model.onnx").apply { writeText("unsafe") }
        try {
            val result = ModelPackVerifier.verify(
                InstalledModelPack(
                    id = "test-pack",
                    rootDirectory = root,
                    files = listOf(ModelFileRequirement("../${outside.name}", outside.sha256())),
                )
            )
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.contains("escapes") })
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
