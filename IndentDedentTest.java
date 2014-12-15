
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class IndentDedentTest {
    
    public static void main(String[] args) {
        org.junit.runner.JUnitCore.main(IndentDedentTest.class.getName());
    }

    private static IndentDedentInputStream createFromString(String s) {
        InputStream original = new ByteArrayInputStream(
                s.getBytes(StandardCharsets.UTF_8));
        return new IndentDedentInputStream(original);
    }

    private static String getString(IndentDedentInputStream st)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        int level = 0;
        while ((c = st.read()) >= 0) {
            sb.append((char) c);
            
            if (! st.keepIndentedWhitespace()) {
                // Only when we remove indented whitespace 
                // for every text check correct indent level
                if ((char)c == st.getIndentChar()) { // during indent & dedent, the internal 
                    ++level;          // indentations are cleared, or raised
                } else if ((char)c ==st.getDedentChar()) {
                    --level;
                } else {             // but if no in/dedent happens, level should not change
                    assertEquals(level, st.getIndentations().size());
                }
            }
        }
        return sb.toString();
    }

    @Test
    public void testNotIndentedText() throws IOException {
        String s = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr,\n"
                + "sed diam nonumy eirmod tempor invidunt ut labore et dolore\n"
                + "magna aliquyam erat, sed diam voluptua. At vero eos et accusam\n"
                + "et justo duo dolores et ea rebum. Stet clita kasd gubergren, no\n"
                + "sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem\n"
                + "ipsum dolor sit amet, consetetur sadipscing elitr, sed diam\n"
                + "nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam\n"
                + "erat, sed diam voluptua. At vero eos et accusam et justo\n"
                + "duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata\n"
                + "sanctus est Lorem ipsum dolor sit amet.";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(s, getString(st));
    }

    @Test
    public void testParBoiledExample() throws IOException {
        String s = "level 1\n" 
                 + "    level 2\n" 
                 + "    still level 2\n"
                 + "      level 3\n" 
                 + "      also 3\n" 
                 + "    2 again\n"
                 + "        another 3\n" 
                 + "and back to 1\n"
                 + " another level 2 again\n";
        String expect = "level 1\n" 
                + ">level 2\n" 
                + "still level 2\n"
                + ">level 3\n" 
                + "also 3\n" 
                + "<2 again\n" 
                + ">another 3\n"
                + "<<and back to 1\n" 
                + ">another level 2 again\n" 
                + "<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testNoNewLineAtEnd() throws IOException {
        String s = "Hello\n  World";
        String expect = "Hello\n>World<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testNewLineAtEnd() throws IOException {
        String s = "Hello\n  World\n";
        String expect = "Hello\n>World\n<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testEmptyLineAtEnd() throws IOException {
        String s = "Hello\n  World\n   ";
        String expect = "Hello\n>World\n   <";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testEmptyLineInBetween() throws IOException {
        String s = "Hello\n  World\n  \n  How are you?";
        String expect = "Hello\n>World\n  \nHow are you?<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testDedentAfterEmptyLine() throws IOException {
        String s = 
                  "Hello\n"
                + " World\n"
                + "  How\n"
                + "  \n"
                + " are you?";
        String expect = 
                  "Hello\n"
                + ">World\n"
                + ">How\n"
                + "  \n"
                + "<are you?<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testStayOnLevel() throws IOException {
        String s = 
                  "Hello\n"
                + "  World\n"
                + "  How are you?";
        String expect = 
                  "Hello\n"
                + ">World\n"
                + "How are you?<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testMultipleLevel() throws IOException {
        String s = 
                  "Hello\n"
                + " World\n"
                + "  How\n"
                + "   are\n"
                + "    you?";
        String expect = 
                  "Hello\n"
                + ">World\n"
                + ">How\n"
                + ">are\n"
                + ">you?<<<<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testInAndDedent() throws IOException {
        String s = 
                  "Hello\n"
                + " World\n"
                + "  How\n "
                + "are\n  "
                + "you?";
        String expect = 
                  "Hello\n"
                + ">World\n"
                + ">How\n"
                + "<are\n"
                + ">you?<<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testLargeLevel() throws IOException {
        String s =      "a\n" +
                        " b\n" +
                        "  c\n" +
                        "   d\n" +
                        "    e\n" +
                        "    e\n" +
                        "     f\n" +
                        "      g\n" +
                        "       h\n" +
                        "        i\n" +
                        "         j\n" +
                        "          k\n" +
                        "a\n" +
                        "   d\n" +
                        "    e\n" +
                        "   d\n" +
                        "   d\n" +
                        "a\n" +
                        " b";
        String expect = 
                        "a\n" +
                        ">b\n" +
                        ">c\n" +
                        ">d\n" +
                        ">e\n" +
                        "e\n" +
                        ">f\n" +
                        ">g\n" +
                        ">h\n" +
                        ">i\n" +
                        ">j\n" +
                        ">k\n" +
                        "<<<<<<<<<<a\n" +
                        ">d\n" +
                        ">e\n" +
                        "<d\n" +
                        "d\n" +
                        "<a\n" +
                        ">b<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test(expected=IOException.class)
    public void testWrongDedent() throws IOException {
        String s = "a\n  c\n b";
        IndentDedentInputStream st = createFromString(s);
        getString(st);
    }
    
    
    @Test
    public void testCorrectMixedIndents() throws IOException {
        String s = 
                  "Hello\n"
                + " World\n"
                + " \tHow\n"
                + " \t are\n"
                + " \t \tyou?";
        String expect = 
                  "Hello\n"
                + ">World\n"
                + ">How\n"
                + ">are\n"
                + ">you?<<<<";
        IndentDedentInputStream st = createFromString(s);
        assertEquals(expect, getString(st));
    }
    
    @Test(expected=IOException.class)
    public void testWrongMixedIndents() throws IOException {
        String s = 
                  "Hello\n"
                + " World\n"
                + " \tHow\n"
                + "  \tare\n"
                + " \t \tyou?";
        IndentDedentInputStream st = createFromString(s);
        getString(st);
    }
    
    @Test
    public void testChangeWhitespace() throws IOException {
        String s = 
                  "Hello\n"
                + "_World\n"
                + "__How\n"
                + "___are\n"
                + "____you?";
        String expect = 
                  "Hello\n"
                + ">World\n"
                + ">How\n"
                + ">are\n"
                + ">you?<<<<";
        IndentDedentInputStream st = createFromString(s);
        st.removeWhitespaceCharacter(' ', '\t');
        st.addWhitespaceCharacter('_');
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testChangeIndentChar() throws IOException {
        String s = 
                  "Hello\n"
                + " World\n"
                + "  How\n"
                + "   are\n"
                + "    you?";
        String expect = 
                  "Hello\n"
                + "{World\n"
                + "{How\n"
                + "{are\n"
                + "{you?}}}}";
        IndentDedentInputStream st = createFromString(s);
        st.setIndentChar('{');
        st.setDedentChar('}');
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testOtherEmptyLineChar() throws IOException {
        String s = 
                  "Hello\n"
                + " World\n"
                + "  How\n"
                + "    \n" // usual empty line
                + "  # comment\n" // single line comment
                + "   are\n"
                + "    you?";
        String expect = 
                  "Hello\n"
                + ">World\n"
                + ">How\n"
                + "    \n"
                + "  # comment\n"
                + ">are\n"
                + ">you?<<<<";
        IndentDedentInputStream st = createFromString(s);
        st.addSingleLineEscape('#');
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testKeepWhitespace() throws IOException {
        String s =      "a\n" +
                        " b\n" +
                        "  c\n" +
                        "   d\n" +
                        "    e\n" +
                        "    e\n" +
                        "     f\n" +
                        "      g\n" +
                        "       h\n" +
                        "        i\n" +
                        "         j\n" +
                        "          k\n" +
                        "a\n" +
                        "   d\n" +
                        "    e\n" +
                        "   d\n" +
                        "   d\n" +
                        "a\n" +
                        " b";
        String expect = 
                        "a\n" +
                        " >b\n" +
                        "  >c\n" +
                        "   >d\n" +
                        "    >e\n" +
                        "    e\n" +
                        "     >f\n" +
                        "      >g\n" +
                        "       >h\n" +
                        "        >i\n" +
                        "         >j\n" +
                        "          >k\n" +
                        "<<<<<<<<<<a\n" +
                        "   >d\n" +
                        "    >e\n" +
                        "   <d\n" +
                        "   d\n" +
                        "<a\n" +
                        " >b<";
        IndentDedentInputStream st = createFromString(s);
        st.setKeepIndentedWhitespace(true);
        assertEquals(expect, getString(st));
    }
    
    @Test
    public void testParboiledWithEscape() throws IOException {
        String s = 
                "level 1\n" +
                "    level 2\n" +
                "    still level 2\n" +
                "      level 3\n" +
                "      also 3\n" +
                "  ( hello\n world\n         bla)\n" +
                "     \n" +
                "  # blaaaa blubb\n" +
                "    2 again\n" +
                "        another 3\n" +
                "and back to 1\n" +
                " another level 2 again\n" +
                "  ";
        String expect = 
                "level 1\n" +
                ">level 2\n" +
                "still level 2\n" +
                ">level 3\n" +
                "also 3\n" +
                "  ( hello\n world\n         bla)\n" +
                "     \n" +
                "  # blaaaa blubb\n" +
                "<2 again\n" +
                ">another 3\n" +
                "<<and back to 1\n" +
                ">another level 2 again\n" +
                "  <";
        IndentDedentInputStream st = createFromString(s);
        st.addSingleLineEscape('#');
        st.addCharEscape('(', ')');
        assertEquals(expect, getString(st));
    }
}
