package snd.komelia.bookmarks

import snd.komga.client.book.KomgaBookId

data class EpubBookmark(
    val id: String,
    val bookId: KomgaBookId,
    val locatorJson: String,
    val createdAt: Long,
)
