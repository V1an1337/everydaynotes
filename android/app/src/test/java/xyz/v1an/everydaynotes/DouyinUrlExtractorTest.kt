package xyz.v1an.everydaynotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DouyinUrlExtractorTest {
    @Test
    fun extractsNoisyDouyinShareUrl() {
        val text = "4.89 复制打开抖音，看看【敦兰的作品】 ... https://v.douyin.com/oSzS4bbF--I/ :1pm gOK:/"
        assertEquals("https://v.douyin.com/oSzS4bbF--I", DouyinUrlExtractor.extract(text))
    }

    @Test
    fun returnsNullWithoutUrl() {
        assertNull(DouyinUrlExtractor.extract("没有链接"))
    }
}

