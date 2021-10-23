package se.wingez

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class UtilsTest {

    @Test
    fun testSplitMany() {
        assertEquals(Utils.splitMany("hej", listOf("test")), listOf("hej"))
        assertEquals(Utils.splitMany("hej", listOf("e")), listOf("h", "j"))
        assertEquals(Utils.splitMany("1,2 3,4&7", listOf(" ", ",")), listOf("1", "2", "3", "4&7"))


    }
}