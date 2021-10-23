package se.wingez

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class UtilsTest {

    @Test
    fun testSplitMany() {
        assertEquals(splitMany("hej", listOf("test")), listOf("hej"))
        assertEquals(splitMany("hej", listOf("e")), listOf("h", "j"))
        assertEquals(splitMany("1,2 3,4&7", listOf(" ", ",")), listOf("1", "2", "3", "4&7"))


    }
}