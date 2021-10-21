package se.wingez;

import kotlin.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    Tokenizer t = new Tokenizer();

    @Test
    void testIndentation() {
        assertEquals(t.getIndentation("test"), new Pair<>(0, "test"));
        assertEquals(t.getIndentation("\ttemp"), new Pair<>(1, "temp"));

        assertEquals(t.getIndentation("rest\t"), new Pair<>(0, "rest\t"));

        for (int i = 0; i < 10; i++) {
            assertEquals(t.getIndentation("\t".repeat(i) + "test"), new Pair<>(i, "test"));
            assertEquals(t.getIndentation("  ".repeat(i) + "test"), new Pair<>(i, "test"));
        }

//
//        with pytest.raises(token.InvalidSyntaxError):
//        token.get_indentation(' test')
//
//        with pytest.raises(token.InvalidSyntaxError):
//        token.get_indentation('  \ttemp')


    }

}