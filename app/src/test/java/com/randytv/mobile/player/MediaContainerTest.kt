package com.randytv.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaContainerTest {
    @Test fun missingOrDecoratedMetadataProducesUsableExtension() {
        assertEquals("mp4", MediaContainer.extension(null))
        assertEquals("mp4", MediaContainer.extension(" "))
        assertEquals("mkv", MediaContainer.extension(" .MKV "))
        assertEquals("ts", MediaContainer.extension("ts"))
    }
}
