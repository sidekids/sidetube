// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.provider

import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.url.YouTubeTarget
import xyz.steier.sidetube.core.url.YouTubeUrlParser

/**
 * Loest eine eingegebene Adresse zu einem Entwurf auf.
 *
 * Reihenfolge nach Kosten: oEmbed und die Kanalseite kommen ohne Schluessel und ohne Kontingent
 * aus und decken alles ab, was Eltern gewoehnlich einfuegen. Die Data API bleibt fuer das, was
 * ohne sie nicht geht – nicht als erster Griff.
 */
class YouTubeResolver(
    private val oEmbed: OEmbedSource,
    private val channelPage: ChannelPageSource
) {

    suspend fun resolve(input: String): ContentDraft {
        val target = YouTubeUrlParser.parse(input) ?: throw ProviderError.Unsupported
        return resolve(target)
    }

    suspend fun resolve(target: YouTubeTarget): ContentDraft = when (target) {
        is YouTubeTarget.Video -> oEmbed.resolve(WhitelistItemType.VIDEO, target.id)
        is YouTubeTarget.Playlist -> oEmbed.resolve(WhitelistItemType.PLAYLIST, target.id)
        is YouTubeTarget.Channel -> channelPage.byId(target.id).asDraft()
        is YouTubeTarget.ChannelHandle -> channelPage.byHandle(target.handle).asDraft()
        is YouTubeTarget.ChannelName -> channelPage.byName(target.name).asDraft()
    }

    private fun ChannelInfo.asDraft() = ContentDraft(
        type = WhitelistItemType.CHANNEL,
        contentId = channelId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        channelTitle = title,
        sourceChannelId = channelId,
        description = description
    )
}
