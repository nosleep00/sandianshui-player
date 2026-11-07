package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class LaraTranslatorTest {

    private val translator = LaraTranslator(
        accessKeyId = "test-access-key-id",
        accessKeySecret = "v_test-secret",
    )

    @Test
    fun `解析翻译响应按顺序对齐`() {
        val json = """
        {"status":200,"content":{"content_type":"text/plain","source_language":"en-US",
          "translation":["你好世界","早上好"]}}
        """.trimIndent()
        val out = translator.parseTranslations(json)
        assertNotNull(out)
        assertEquals(2, out!!.size)
        assertEquals("你好世界", out[0])
        assertEquals("早上好", out[1])
    }

    @Test
    fun `响应缺少 content 返回 null`() {
        assertEquals(null, translator.parseTranslations("""{"status":500}"""))
    }

    @Test
    fun `规范串格式正确`() {
        val canonical = translator.buildCanonical(
            "POST", "/v2/auth", "md5base64", "application/json", "Fri, 23 Oct 2026 14:30:00 GMT",
        )
        assertEquals(
            "POST\n/v2/auth\nmd5base64\napplication/json\nFri, 23 Oct 2026 14:30:00 GMT",
            canonical,
        )
    }

    @Test
    fun `HMAC-SHA256 签名与参考实现一致`() {
        // 独立计算 HMAC-SHA256 作为参考
        val secret = "v_test-secret"
        val message = "POST\n/v2/auth\nmd5\napplication/json\ndate"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(message.toByteArray()))
        assertEquals(expected, translator.hmacSha256Base64(secret, message))
    }
}
