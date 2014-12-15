
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This InputStream transforms context afflicted indentation levels of the input 
 * into context free parsable output. Whenever there is an indentation, it replaces
 * the whitespace (default ' ' or '\t') into an indent character (default
 * '>'). When the indentation level is reduced, it outputs the corresponding amount of
 * dedent characters (default '<'). Empty lines are ignored. Hence an input like:
 * 
 * <pre>
 * {@code
 * level 1
 *     level 2
 *     still level 2
 *       level 3
 *       
 *       also 3
 *     2 again
 *         another 3
 * and back to 1
 *  another level 2 again
 * }
 * </pre>
 * 
 * will be transformed into:
 * 
 * <pre>
 * {@code
 * level 1
 * >level 2
 * still level 2
 * >level 3
 *         
 * also 3
 * <2 again
 * >another 3
 * <<and back to 1
 * >another level 2 again
 * }
 * </pre>
 * 
 * The characters, which will be recognized as whitespace responsible for indentation
 * can be changed by adding/ removing whitespace characters 
 * (see {@link #addWhitespaceCharacter(char...)}, 
 * {@link #removeWhitespaceCharacter(char...)}). 
 * <p>
 * The replacement characters for indents and dedents can be changed with 
 * {@link #setIndentChar(char)}
 * and {@link #setDedentChar(char)}.
 * <p>
 * When you want to keep the whitespace of indents/ dedents, set the flag 
 * {@link #setKeepIndentedWhitespace(boolean)} to <code>true</code>.
 * <p>
 * If you want single lines to be ignored (similar to empty lines), e.g. for
 * ignoring indentation of single comment lines, you can add 
 * {@link #addSingleLineEscape(char)}. If you want to ignore indentation rules
 * for multiple lines you can either add a {@link #addCharEscape(char, char)} or
 * implement your own {@link IEscapeIndentDedentRule} and add it with 
 * {@link #addEscape(IEscapeIndentDedentRule)}.
 * 
 * @author tammoippen
 *
 */
public class IndentDedentInputStream extends InputStream {
    
    public class EscapeIndentDedentRule {
        private final char open;
        private final char close;
        
        private EscapeIndentDedentRule(char open, char close) {
            super();
            this.open = open;
            this.close = close;
        }

        public char getOpen() {
            return open;
        }

        public char getClose() {
            return close;
        }
    }

    public class IndentLevel {
        private final String indentation;

        public IndentLevel(String indentation) {
            assert (indentation != null);
            assert (indentation.length() > 0);
            this.indentation = indentation;
        }

        public String getIndentation() {
            return indentation;
        }

        public boolean isPrefixOf(String o) {
            assert (o != null);
            return o.startsWith(indentation);
        }

        public int length() {
            return indentation.length();
        }
    }

    private final InputStream original_stream;
    private final List<IndentLevel> indentations;

    // Sometimes i need to peek in the future, hence i keep
    // the read characters in this readbuffer.
    private final List<Integer> readbuffer;
    private final Set<Character> whitespaceCharacter;
    
    private final List<EscapeIndentDedentRule> escapes;
    private EscapeIndentDedentRule current_escape;

    private char indent_character;
    private char dedent_character;
    private char newline_character;

    private boolean last_read_newline;
    private boolean keep_indented_whitespace;

    public IndentDedentInputStream(final InputStream original_stream) {
        this.original_stream = original_stream;
        this.indentations = new ArrayList<IndentLevel>();
        this.escapes = new ArrayList<EscapeIndentDedentRule>();
        this.current_escape = null;
        this.keep_indented_whitespace = false;
        // assume we read a newline befor the stream:
        // what if the original stream starts with an indentation?
        this.last_read_newline = true;
        this.indent_character = '>';
        this.dedent_character = '<';
        this.newline_character = '\n';
        this.readbuffer = new LinkedList<Integer>();
        this.whitespaceCharacter = new HashSet<Character>(Arrays.asList(' ', '\t'));
    }

    @Override
    public int read() throws IOException {
        int currentRead;
        if (!readbuffer.isEmpty()) {
            currentRead = readbuffer.remove(0);
        } else {
            currentRead = original_stream.read();
            checkEscapes(currentRead);
        }
        if (currentRead < 0) { // exit of InputStream iterations
            // remove all indents from indentations
            for (int i = 0; i < indentations.size(); i++) {
                readbuffer.add((int) dedent_character); // add a dedent char to
                                                        // readbuffer
            }
            readbuffer.add(currentRead); // add closing char
            indentations.clear();
            currentRead = readbuffer.remove(0); // get first dedent character
            return currentRead;
        }
        
        if (current_escape != null) {
            if (current_escape.getClose() == (char)currentRead) {
                current_escape = null;
            } else {
                last_read_newline = false; // do not check indent level
            }
        }
        
        if (last_read_newline) {
            // check indent level
            StringBuilder b = new StringBuilder();
            boolean escape = false;
            while (whitespaceCharacter.contains((char) currentRead)) {
                b.append((char) currentRead);
                readbuffer.add(currentRead);
                currentRead = original_stream.read();
                escape = checkEscapes(currentRead);
            }
            // ignore empty lines, e.g.
            // whitespace only lines with an newline at the end
            // and check, that there is no escape starting
            if (currentRead == (int)'\n' || currentRead == -1 || escape) {
                readbuffer.add(currentRead);
                currentRead = readbuffer.remove(0);
            } else {
                currentRead = updateIndentations(b, currentRead);
            }
        }

        // trigger possible indent/dedent in next line.
        last_read_newline = currentRead == (int) newline_character;
        return currentRead;
    }

    /**
     * Compares the read whitespace characters (in the StringBuilder indent_ws) 
     * with the current indentation level (in {@link #getIndentations()}). 
     * From left to right it checks, if the previously observed indent is a 
     * prefix of indent_ws. If <code>true</code>, the indent is stripped from the
     * indent_ws and the next indent in {@link #getIndentations()} is checked.
     * It stops either, if there are no more indents in {@link #getIndentations()} 
     * or if the prefix indent_ws and some indent do not match.
     * @param indent_ws
     * @return Correctly matched indentation level.
     */
    protected int getLevel(StringBuilder indent_ws) {
        int i;
        for (i = 0; i < indentations.size(); ++i) {
            IndentLevel currIndent = indentations.get(i);
            if (currIndent.isPrefixOf(indent_ws.toString())) {
                // everything is fine... remove prefix
                indent_ws.replace(0, currIndent.length(), "");
            } else {
                break;
            }
        }
        return i;
    }
    
    /**
     * Check if one escape rule for {@link IEscapeIndentDedentRule#open()} is true.
     * @param currentRead
     * @return
     */
    protected boolean checkEscapes(final int currentRead) {
        if (current_escape == null) {
            for (EscapeIndentDedentRule e : escapes) {
                if (e.getOpen() == (char)currentRead) {
                    current_escape = e;
                    break;
                }
            }
        }
        return current_escape != null;
    }

    protected int updateIndentations(StringBuilder b, final int currentRead)
            throws IOException {
        int level = getLevel(b);
        if (level == indentations.size()) {
            if (b.length() == 0) {
                // same level
                // everything is fine
                if (! keep_indented_whitespace) {
                    readbuffer.clear(); // throw away indent whitespace
                }
                readbuffer.add(currentRead);
                return readbuffer.remove(0); // get next char
            } else {
                // b.length() > 0 and b only contains indent characters
                // everything is fine
                // we got an indent
                indentations.add(new IndentLevel(b.toString()));
                if (! keep_indented_whitespace) {
                    readbuffer.clear(); // replace indent whitespace with:
                }
                readbuffer.add((int)indent_character);
                readbuffer.add(currentRead);
                return readbuffer.remove(0); // get next char
            }
        } else if (level < indentations.size()) {
            if (b.length() == 0) {
                // everything is fine
                // we got one or more dedents
                if (! keep_indented_whitespace) {
                    readbuffer.clear(); // throw away indent whitespace
                }
                int number_of_dedents = indentations.size() - level;
                for (int i = 0; i < number_of_dedents; i++) {
                    indentations.remove(indentations.size() - 1); // remove last
                                                                  // indentation
                    readbuffer.add((int) dedent_character); // add a dedent char
                                                            // to readbuffer
                }
                readbuffer.add(currentRead);
                return readbuffer.remove(0); // get first dedent character
            } else {
                // some error occured
                // unequal number of indent levels and
                // still indent whitespace available
                throw new IOException("Indentation error");
            }
        } else {
            // error, should never occure
            throw new IOException("Unexpected error.");
        }
    }

    @Override
    public void close() throws IOException {
        original_stream.close();
    }

    @Override
    public int available() throws IOException {
        if (readbuffer.size() > 0) {
          return readbuffer.size();
        } else {
            return original_stream.available();
        }
    }

    public InputStream getOriginalStream() {
        return original_stream;
    }

    /**
     * @return A unmodifiable list with the current {@link IndentLevel}.
     */
    public List<IndentLevel> getIndentations() {
        return Collections.unmodifiableList(indentations);
    }

    /**
     * @return The indentation replacement character.
     */
    public char getIndentChar() {
        return indent_character;
    }

    /**
     * Set the indentation replacement character.
     * @param indent
     */
    public void setIndentChar(char indent) {
        this.indent_character = indent;
    }

    /**
     * @return The dedent replacement character.
     */
    public char getDedentChar() {
        return dedent_character;
    }

    /**
     * Set the dedent replacement character.
     * @param indent
     */
    public void setDedentChar(char dedent) {
        this.dedent_character = dedent;
    }

    public char getNewlineCharacter() {
        return newline_character;
    }

    public void setNewlineCharacter(char newline_character) {
        this.newline_character = newline_character;
    }
    
    /**
     * Add a list of chars, which will be regarded as indentation responsible
     * characters.
     * @param cs
     */
    public void addWhitespaceCharacter(char...cs) {
        for (int i = 0; i < cs.length; i++) {
            this.whitespaceCharacter.add(cs[i]);
        }
    }
    
    /**
     * The given chars will not be regarded as indentation responsible
     * characters anymore.
     * @param cs
     */
    public void removeWhitespaceCharacter(char...cs) {
        for (int i = 0; i < cs.length; i++) {
            this.whitespaceCharacter.remove(cs[i]);
        }
    }
    
    /**
     * Add an escape rule, which will cause indentation rules to be 
     * ignored between the start and the end character.
     * @param start
     * @param end
     */
    public void addCharEscape(char start, char end) {
        this.escapes.add(new EscapeIndentDedentRule(start, end));
    }
    
    /**
     * Add an escape rule, which will cause indentation rules to be 
     * ignored between the start and the next newline character.
     * @param start
     * @param end
     */
    public void addSingleLineEscape(final char start) {
        addCharEscape(start, newline_character);
    }

    /**
     * States, whether the indented whitespace will be kept (default <code>false</code>).
     */
    public boolean keepIndentedWhitespace() {
        return keep_indented_whitespace;
    }

    /**
     * Set, whether the indented whitespace will be kept (default <code>false</code>).
     */
    public void setKeepIndentedWhitespace(boolean keep_indented_whitespace) {
        this.keep_indented_whitespace = keep_indented_whitespace;
    }
}
