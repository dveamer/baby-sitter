package com.dveamer.babysitter.collect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YamNetCryClassifierTest {

    @Test
    fun `울음과 칭얼거림 YAMNet 클래스를 distress로 포함한다`() {
        assertTrue(isYamNetDistressCategory(index = 19, name = ""))
        assertTrue(isYamNetDistressCategory(index = 20, name = ""))
        assertTrue(isYamNetDistressCategory(index = 21, name = ""))
        assertTrue(isYamNetDistressCategory(index = -1, name = "Crying, sobbing"))
        assertTrue(isYamNetDistressCategory(index = -1, name = "Baby cry, infant cry"))
        assertTrue(isYamNetDistressCategory(index = -1, name = "Whimper"))
    }

    @Test
    fun `범위가 넓은 소리 클래스는 distress 후보에 포함하지 않는다`() {
        assertFalse(isYamNetDistressCategory(index = 22, name = "Wail, moan"))
        assertFalse(isYamNetDistressCategory(index = 11, name = "Screaming"))
        assertFalse(isYamNetDistressCategory(index = 4, name = "Babbling"))
    }
}
