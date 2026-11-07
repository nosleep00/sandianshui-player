package com.sandianshui.player.core.media

/**
 * SAF 文档路径的纯字符串工具，不依赖 Android，便于单元测试。
 */
object SafPaths {

    /**
     * 由文档 id 推导其父文档 id。
     * 例如 externalstorage 的「primary:Music/x/a.wav」→「primary:Music/x」；
     * 已处于根目录（没有 '/'）时返回 null。
     */
    fun parentDocumentId(documentId: String): String? {
        val slash = documentId.lastIndexOf('/')
        if (slash <= 0) return null
        return documentId.substring(0, slash)
    }

    /** 文件名去掉最后一个扩展名，如「静夜思.wav」→「静夜思」。 */
    fun baseName(fileName: String): String = fileName.substringBeforeLast('.')

    /**
     * 取文档 id 的最后一级名称。
     * 例如「primary:Asmr/audio」→「audio」；「primary:Asmr」→「Asmr」。
     */
    fun leafName(documentId: String): String {
        val slash = documentId.lastIndexOf('/')
        val tail = if (slash >= 0) documentId.substring(slash + 1) else documentId
        val colon = tail.lastIndexOf(':')
        return if (colon >= 0) tail.substring(colon + 1) else tail
    }
}
