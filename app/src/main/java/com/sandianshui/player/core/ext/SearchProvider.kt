package com.sandianshui.player.core.ext

/**
 * 资源搜索提供者（扩展预留，本期不实现）。
 * 未来接入多个音源时实现该接口即可，主流程无需改动。
 */
interface SearchProvider {
    val id: String
    val displayName: String

    suspend fun search(keyword: String): List<OnlineMedia>
}
