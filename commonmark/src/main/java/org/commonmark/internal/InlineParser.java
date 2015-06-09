package org.commonmark.internal;

import org.commonmark.parser.DelimiterProcessor;
import org.commonmark.internal.inline.AsteriskDelimiterProcessor;
import org.commonmark.internal.inline.UnderscoreDelimiterProcessor;
import org.commonmark.internal.util.Escaping;
import org.commonmark.internal.util.Html5Entities;
import org.commonmark.node.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InlineParser {

    private static final char C_NEWLINE = '\n';
    private static final char C_UNDERSCORE = '_';
    private static final char C_BACKTICK = '`';
    private static final char C_OPEN_BRACKET = '[';
    private static final char C_CLOSE_BRACKET = ']';
    private static final char C_LESSTHAN = '<';
    private static final char C_BANG = '!';
    private static final char C_BACKSLASH = '\\';
    private static final char C_AMPERSAND = '&';
    private static final char C_OPEN_PAREN = '(';
    private static final char C_CLOSE_PAREN = ')';
    private static final char C_COLON = ':';

    private static final String ESCAPED_CHAR = "\\\\" + Escaping.ESCAPABLE;
    private static final String REG_CHAR = "[^\\\\()\\x00-\\x20]";
    private static final String IN_PARENS_NOSP = "\\((" + REG_CHAR + '|' + ESCAPED_CHAR + ")*\\)";
    private static final String TAGNAME = "[A-Za-z][A-Za-z0-9]*";
    private static final String ATTRIBUTENAME = "[a-zA-Z_:][a-zA-Z0-9:._-]*";
    private static final String UNQUOTEDVALUE = "[^\"'=<>`\\x00-\\x20]+";
    private static final String SINGLEQUOTEDVALUE = "'[^']*'";
    private static final String DOUBLEQUOTEDVALUE = "\"[^\"]*\"";
    private static final String ATTRIBUTEVALUE = "(?:" + UNQUOTEDVALUE + "|" + SINGLEQUOTEDVALUE
            + "|" + DOUBLEQUOTEDVALUE + ")";
    private static final String ATTRIBUTEVALUESPEC = "(?:" + "\\s*=" + "\\s*" + ATTRIBUTEVALUE
            + ")";
    private static final String ATTRIBUTE = "(?:" + "\\s+" + ATTRIBUTENAME + ATTRIBUTEVALUESPEC
            + "?)";
    private static final String OPENTAG = "<" + TAGNAME + ATTRIBUTE + "*" + "\\s*/?>";
    private static final String CLOSETAG = "</" + TAGNAME + "\\s*[>]";
    private static final String HTMLCOMMENT = "<!---->|<!--(?:-?[^>-])(?:-?[^-])*-->";
    private static final String PROCESSINGINSTRUCTION = "[<][?].*?[?][>]";
    private static final String DECLARATION = "<![A-Z]+" + "\\s+[^>]*>";
    private static final String CDATA = "<!\\[CDATA\\[[\\s\\S]*?\\]\\]>";
    private static final String HTMLTAG = "(?:" + OPENTAG + "|" + CLOSETAG + "|" + HTMLCOMMENT
            + "|" + PROCESSINGINSTRUCTION + "|" + DECLARATION + "|" + CDATA + ")";
    private static final String ENTITY = "&(?:#x[a-f0-9]{1,8}|#[0-9]{1,8}|[a-z][a-z0-9]{1,31});";

    private static final String ASCII_PUNCTUATION = "'!\"#\\$%&\\(\\)\\*\\+,\\-\\./:;<=>\\?@\\[\\\\\\]\\^_`\\{\\|\\}~";
    private static final Pattern PUNCTUATION = Pattern
            .compile("^[" + ASCII_PUNCTUATION + "\\p{Pc}\\p{Pd}\\p{Pe}\\p{Pf}\\p{Pi}\\p{Po}\\p{Ps}]");

    private static final Pattern HTML_TAG = Pattern.compile('^' + HTMLTAG, Pattern.CASE_INSENSITIVE);

    private static final Pattern LINK_TITLE = Pattern.compile(
            "^(?:\"(" + ESCAPED_CHAR + "|[^\"\\x00])*\"" +
                    '|' +
                    "'(" + ESCAPED_CHAR + "|[^'\\x00])*'" +
                    '|' +
                    "\\((" + ESCAPED_CHAR + "|[^)\\x00])*\\))");

    private static final Pattern LINK_DESTINATION_BRACES = Pattern.compile(
            "^(?:[<](?:[^<>\\n\\\\\\x00]" + '|' + ESCAPED_CHAR + '|' + "\\\\)*[>])");

    private static final Pattern LINK_DESTINATION = Pattern.compile(
            "^(?:" + REG_CHAR + "+|" + ESCAPED_CHAR + '|' + IN_PARENS_NOSP + ")*");

    private static final Pattern LINK_LABEL = Pattern
            .compile("^\\[(?:[^\\\\\\[\\]]|\\\\[\\[\\]]){0,1000}\\]");

    private static final Pattern ESCAPABLE = Pattern.compile(Escaping.ESCAPABLE);

    private static final Pattern ENTITY_HERE = Pattern.compile('^' + ENTITY, Pattern.CASE_INSENSITIVE);

    private static final Pattern TICKS = Pattern.compile("`+");

    private static final Pattern TICKS_HERE = Pattern.compile("^`+");

    private static final Pattern EMAIL_AUTOLINK = Pattern
            .compile("^<([a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)>");

    private static final Pattern AUTOLINK = Pattern
            .compile("^<(?:coap|doi|javascript|aaa|aaas|about|acap|cap|cid|crid|data|dav|dict|dns|file|ftp|geo|go|gopher|h323|http|https|iax|icap|im|imap|info|ipp|iris|iris.beep|iris.xpc|iris.xpcs|iris.lwz|ldap|mailto|mid|msrp|msrps|mtqp|mupdate|news|nfs|ni|nih|nntp|opaquelocktoken|pop|pres|rtsp|service|session|shttp|sieve|sip|sips|sms|snmp|soap.beep|soap.beeps|tag|tel|telnet|tftp|thismessage|tn3270|tip|tv|urn|vemmi|ws|wss|xcon|xcon-userid|xmlrpc.beep|xmlrpc.beeps|xmpp|z39.50r|z39.50s|adiumxtra|afp|afs|aim|apt|attachment|aw|beshare|bitcoin|bolo|callto|chrome|chrome-extension|com-eventbrite-attendee|content|cvs|dlna-playsingle|dlna-playcontainer|dtn|dvb|ed2k|facetime|feed|finger|fish|gg|git|gizmoproject|gtalk|hcp|icon|ipn|irc|irc6|ircs|itms|jar|jms|keyparc|lastfm|ldaps|magnet|maps|market|message|mms|ms-help|msnim|mumble|mvn|notes|oid|palm|paparazzi|platform|proxy|psyc|query|res|resource|rmi|rsync|rtmp|secondlife|sftp|sgn|skype|smb|soldat|spotify|ssh|steam|svn|teamspeak|things|udp|unreal|ut2004|ventrilo|view-source|webcal|wtai|wyciwyg|xfire|xri|ymsgr):[^<>\u0000-\u0020]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern SPNL = Pattern.compile("^ *(?:\n *)?");

    private static final Pattern WHITESPACE_CHAR = Pattern.compile("^\\p{IsWhite_Space}");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern FINAL_SPACE = Pattern.compile(" *$");

    private static final Pattern LINE_END = Pattern.compile("^ *(?:\n|$)");

    private static final Pattern INITIAL_SPACE = Pattern.compile("^ *");

    /**
     * Matches a string of non-special characters.
     */
    private final Pattern mainPattern;
    private final Map<Character, DelimiterProcessor> delimiterProcessors = new HashMap<>();

    /**
     * Link references by ID, needs to be built up using parseReference before calling parse.
     */
    private Map<String, Link> referenceMap = new HashMap<>();

    private Node block;
    private String subject;
    private int pos;
    /**
     * Stack of delimiters (emphasis, strong emphasis).
     */
    private Delimiter delimiter;

    private StringBuilder currentText;

    public InlineParser(List<DelimiterProcessor> customDelimiterProcessors) {
        addDelimiterProcessors(Arrays.<DelimiterProcessor>asList(new AsteriskDelimiterProcessor(), new UnderscoreDelimiterProcessor()));
        addDelimiterProcessors(customDelimiterProcessors);
        mainPattern = calculateMainPattern(delimiterProcessors.keySet());
    }

    private void addDelimiterProcessors(Iterable<DelimiterProcessor> delimiterProcessors) {
        for (DelimiterProcessor delimiterProcessor : delimiterProcessors) {
            char c = delimiterProcessor.getDelimiterChar();
            DelimiterProcessor existing = this.delimiterProcessors.put(c, delimiterProcessor);
            if (existing != null) {
                throw new IllegalArgumentException("Inline delimiter parser can not be registered more than once, delimiter character: " + c);
            }
        }
    }

    private static Pattern calculateMainPattern(Iterable<Character> delimiterSet) {
        StringBuilder sb = new StringBuilder();
        for (Character delimiterCharacter : delimiterSet) {
            sb.append('\\');
            sb.append(delimiterCharacter);
        }
        // Don't skip delimiter characters, they need special processing
        String delimiterCharacters = sb.toString();
        return Pattern.compile("^[^\n`\\[\\]\\\\!<&" + delimiterCharacters + "]+");
    }

    /**
     * Parse content in block into inline children, using reference map to resolve references.
     */
    public void parse(Node block, String content) {
        this.block = block;
        this.subject = content.trim();
        this.pos = 0;
        this.delimiter = null;

        boolean moreToParse;
        do {
            moreToParse = parseInline();
        } while (moreToParse);
        flushTextNode();

        processDelimiters(null);
    }

    /**
     * Attempt to parse a link reference, modifying the internal reference map.
     *
     * @return how many characters were parsed as a reference, {@code 0} if none
     */
    public int parseReference(String s) {
        this.subject = s;
        this.pos = 0;
        String rawLabel;
        String dest;
        String title;
        int matchChars;
        int startPos = this.pos;

        // label:
        matchChars = this.parseLinkLabel();
        if (matchChars == 0) {
            return 0;
        } else {
            rawLabel = this.subject.substring(0, matchChars);
        }

        // colon:
        if (this.peek() == C_COLON) {
            this.pos++;
        } else {
            this.pos = startPos;
            return 0;
        }

        // link url
        this.spnl();

        dest = this.parseLinkDestination();
        if (dest == null || dest.length() == 0) {
            this.pos = startPos;
            return 0;
        }

        int beforeTitle = this.pos;
        this.spnl();
        title = this.parseLinkTitle();
        if (title == null) {
            // rewind before spaces
            this.pos = beforeTitle;
        }

        // make sure we're at line end:
        if (this.pos != this.subject.length() && this.match(LINE_END) == null) {
            this.pos = startPos;
            return 0;
        }

        String normalizedLabel = Escaping.normalizeReference(rawLabel);

        if (!referenceMap.containsKey(normalizedLabel)) {
            Link link = new Link(dest, title);
            referenceMap.put(normalizedLabel, link);
        }
        return this.pos - startPos;
    }

    private void appendText(CharSequence text) {
        if (currentText != null) {
            currentText.append(text);
        } else {
            currentText = new StringBuilder(text);
        }
    }

    private void appendNode(Node node) {
        flushTextNode();
        block.appendChild(node);
    }

    // In some cases, we don't want the text to be appended to an existing node, we need it separate
    private Text appendSeparateText(String text) {
        Text node = new Text(text);
        appendNode(node);
        return node;
    }

    private void flushTextNode() {
        if (currentText != null) {
            block.appendChild(new Text(currentText.toString()));
            currentText = null;
        }
    }

    /**
     * Parse the next inline element in subject, advancing subject position.
     * On success, add the result to block's children and return true.
     * On failure, return false.
     */
    private boolean parseInline() {
        boolean res;
        char c = this.peek();
        if (c == '\0') {
            return false;
        }
        switch (c) {
            case C_NEWLINE:
                res = this.parseNewline();
                break;
            case C_BACKSLASH:
                res = this.parseBackslash();
                break;
            case C_BACKTICK:
                res = this.parseBackticks();
                break;
            case C_OPEN_BRACKET:
                res = this.parseOpenBracket();
                break;
            case C_BANG:
                res = this.parseBang();
                break;
            case C_CLOSE_BRACKET:
                res = this.parseCloseBracket();
                break;
            case C_LESSTHAN:
                res = this.parseAutolink() || this.parseHtmlTag();
                break;
            case C_AMPERSAND:
                res = this.parseEntity();
                break;
            default:
                DelimiterProcessor inlineDelimiter = delimiterProcessors.get(c);
                if (inlineDelimiter != null) {
                    res = parseDelimiters(inlineDelimiter);
                } else {
                    res = this.parseString();
                }
                break;
        }
        if (!res) {
            this.pos += 1;
            // When we get here, it's only for a single special character that turned out to not have a special meaning.
            // So we shouldn't have a single surrogate here, hence it should be ok to turn it into a String.
            String literal = String.valueOf(c);
            appendText(literal);
        }

        return true;
    }

    /**
     * If re matches at current position in the subject, advance position in subject and return the match; otherwise
     * return null.
     */
    private String match(Pattern re) {
        if (this.pos >= this.subject.length()) {
            return null;
        }
        Matcher matcher = re.matcher(this.subject.substring(this.pos));
        boolean m = matcher.find();
        if (m) {
            this.pos += matcher.end();
            return matcher.group();
        } else {
            return null;
        }
    }

    /**
     * Returns the char at the current subject position, or {@code '\0'} in case there are no more characters.
     */
    private char peek() {
        if (this.pos < this.subject.length()) {
            return this.subject.charAt(this.pos);
        } else {
            return '\0';
        }
    }

    /**
     * Parse zero or more space characters, including at most one newline.
     */
    private boolean spnl() {
        this.match(SPNL);
        return true;
    }

    /**
     * Parse a newline. If it was preceded by two spaces, return a hard line break; otherwise a soft line break.
     */
    private boolean parseNewline() {
        this.pos += 1; // assume we're at a \n

        // We're gonna add a new node in any case and we need to check the last text node, so flush outstanding text.
        flushTextNode();

        Node lastChild = block.getLastChild();
        // Check previous node for trailing spaces
        if (lastChild != null && lastChild instanceof Text) {
            Text text = (Text) lastChild;
            Matcher matcher = FINAL_SPACE.matcher(text.getLiteral());
            int sps = matcher.find() ? matcher.end() - matcher.start() : 0;
            if (sps > 0) {
                text.setLiteral(matcher.replaceAll(""));
            }
            appendNode(sps >= 2 ? new HardLineBreak() : new SoftLineBreak());
        } else {
            appendNode(new SoftLineBreak());
        }
        this.match(INITIAL_SPACE); // gobble leading spaces in next line
        return true;
    }

    /**
     * Parse a backslash-escaped special character, adding either the escaped  character, a hard line break
     * (if the backslash is followed by a newline), or a literal backslash to the block's children.
     */
    private boolean parseBackslash() {
        String subj = this.subject;
        int pos = this.pos;
        Node node;
        if (subj.charAt(pos) == C_BACKSLASH) {
            int next = pos + 1;
            if (next < subj.length() && subj.charAt(next) == '\n') {
                this.pos = this.pos + 2;
                node = new HardLineBreak();
                appendNode(node);
            } else if (next < subj.length() && ESCAPABLE.matcher(subj.substring(next, next + 1)).matches()) {
                this.pos = this.pos + 2;
                appendText(subj.substring(next, next + 1));
            } else {
                this.pos++;
                appendText("\\");
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Attempt to parse backticks, adding either a backtick code span or a literal sequence of backticks.
     */
    private boolean parseBackticks() {
        String ticks = this.match(TICKS_HERE);
        if (ticks == null) {
            return false;
        }
        int afterOpenTicks = this.pos;
        String matched;
        while ((matched = this.match(TICKS)) != null) {
            if (matched.equals(ticks)) {
                Code node = new Code();
                String content = this.subject.substring(afterOpenTicks, this.pos - ticks.length());
                String literal = WHITESPACE.matcher(content.trim()).replaceAll(" ");
                node.setLiteral(literal);
                appendNode(node);
                return true;
            }
        }
        // If we got here, we didn't match a closing backtick sequence.
        this.pos = afterOpenTicks;
        appendText(ticks);
        return true;
    }

    /**
     * Attempt to parse delimiters like emphasis, strong emphasis or custom delimiters.
     */
    private boolean parseDelimiters(DelimiterProcessor inlineDelimiter) {
        DelimiterRun res = this.scanDelims(inlineDelimiter);
        if (res == null) {
            return false;
        }
        int numDelims = res.count;
        int startPos = this.pos;

        this.pos += numDelims;
        Text node = appendSeparateText(this.subject.substring(startPos, this.pos));

        // Add entry to stack for this opener
        this.delimiter = new Delimiter(node, this.delimiter, startPos);
        this.delimiter.delimiterChar = inlineDelimiter.getDelimiterChar();
        this.delimiter.numDelims = numDelims;
        this.delimiter.canOpen = res.canOpen;
        this.delimiter.canClose = res.canClose;
        if (this.delimiter.previous != null) {
            this.delimiter.previous.next = this.delimiter;
        }

        return true;
    }

    /**
     * Add open bracket to delimiter stack and add a text node to block's children.
     */
    private boolean parseOpenBracket() {
        int startPos = this.pos;
        this.pos += 1;

        Text node = appendSeparateText("[");

        // Add entry to stack for this opener
        this.delimiter = new Delimiter(node, this.delimiter, startPos);
        this.delimiter.delimiterChar = C_OPEN_BRACKET;
        this.delimiter.numDelims = 1;
        this.delimiter.canOpen = true;
        this.delimiter.canClose = false;
        this.delimiter.allowed = true;
        if (this.delimiter.previous != null) {
            this.delimiter.previous.next = this.delimiter;
        }

        return true;
    }

    /**
     * If next character is [, and ! delimiter to delimiter stack and add a text node to block's children.
     * Otherwise just add a text node.
     */
    private boolean parseBang() {
        int startPos = this.pos;
        this.pos += 1;
        if (this.peek() == C_OPEN_BRACKET) {
            this.pos += 1;

            Text node = appendSeparateText("![");

            // Add entry to stack for this opener
            this.delimiter = new Delimiter(node, this.delimiter, startPos + 1);
            this.delimiter.delimiterChar = C_BANG;
            this.delimiter.numDelims = 1;
            this.delimiter.canOpen = true;
            this.delimiter.canClose = false;
            this.delimiter.allowed = true;
            if (this.delimiter.previous != null) {
                this.delimiter.previous.next = this.delimiter;
            }
        } else {
            appendText("!");
        }
        return true;
    }

    /**
     * Try to match close bracket against an opening in the delimiter stack. Add either a link or image, or a
     * plain [ character, to block's children. If there is a matching delimiter, remove it from the delimiter stack.
     */
    private boolean parseCloseBracket() {
        this.pos += 1;
        int startPos = this.pos;

        // look through stack of delimiters for a [ or ![
        Delimiter opener = this.delimiter;
        while (opener != null) {
            if (!opener.matched && (opener.delimiterChar == C_OPEN_BRACKET || opener.delimiterChar == C_BANG)) {
                break;
            }
            opener = opener.previous;
        }

        if (opener == null) {
            // No matched opener, just return a literal.
            appendText("]");
            return true;
        }

        if (!opener.allowed) {
            // Matching opener but it's not allowed, just return a literal.
            appendText("]");
            // We could remove the opener now, but that would complicate text node merging. So just skip it next time.
            opener.matched = true;
            return true;
        }

        // Check to see if we have a link/image

        String dest = null;
        String title = null;
        boolean isLinkOrImage = false;

        // Inline link?
        if (this.peek() == C_OPEN_PAREN) {
            this.pos++;
            this.spnl();
            if ((dest = this.parseLinkDestination()) != null) {
                this.spnl();
                // title needs a whitespace before
                if (WHITESPACE_CHAR.matcher(this.subject.substring(this.pos - 1, this.pos)).matches()) {
                    title = this.parseLinkTitle();
                    this.spnl();
                }
                if (this.subject.charAt(this.pos) == C_CLOSE_PAREN) {
                    this.pos += 1;
                    isLinkOrImage = true;
                }
            }
        } else { // maybe reference link

            // See if there's a link label
            this.spnl();

            int beforeLabel = this.pos;
            int labelLength = this.parseLinkLabel();
            String ref;
            if (labelLength == 0 || labelLength == 2) {
                // empty or missing second label
                ref = this.subject.substring(opener.index, startPos);
            } else {
                ref = this.subject.substring(beforeLabel, beforeLabel + labelLength);
            }
            if (labelLength == 0) {
                // If shortcut reference link, rewind before spaces we skipped.
                this.pos = startPos;
            }

            Link link = referenceMap.get(Escaping.normalizeReference(ref));
            if (link != null) {
                dest = link.getDestination();
                title = link.getTitle();
                isLinkOrImage = true;
            }
        }

        if (isLinkOrImage) {
            // If we got here, open is a potential opener
            boolean isImage = opener.delimiterChar == C_BANG;
            Node linkOrImage = isImage ? new Image(dest, title) : new Link(dest, title);

            // Flush text now. We don't need to worry about combining it with adjacent text nodes, as we'll wrap it in a
            // link or image node.
            flushTextNode();

            Node node = opener.node.getNext();
            while (node != null) {
                Node next = node.getNext();
                linkOrImage.appendChild(node);
                node = next;
            }
            appendNode(linkOrImage);

            // Process delimiters such as emphasis inside link/image
            processDelimiters(opener);
            removeDelimiterAndNode(opener);

            // Links within links are not allowed. We found this link, so there can be no other link around it.
            if (!isImage) {
                Delimiter delim = this.delimiter;
                while (delim != null) {
                    if (delim.delimiterChar == C_OPEN_BRACKET) {
                        // Disallow link opener. It will still get matched, but will not result in a link.
                        delim.allowed = false;
                    }
                    delim = delim.previous;
                }
            }

            return true;

        } else { // no link or image

            appendText("]");
            // We could remove the opener now, but that would complicate text node merging.
            // E.g. `[link] (/uri)` isn't a link because of the space, so we want to keep appending text.
            opener.matched = true;
            this.pos = startPos;
            return true;
        }
    }

    /**
     * Attempt to parse link destination, returning the string or null if no match.
     */
    private String parseLinkDestination() {
        String res = this.match(LINK_DESTINATION_BRACES);
        if (res != null) { // chop off surrounding <..>:
            if (res.length() == 2) {
                return "";
            } else {
                return Escaping.normalizeURI(Escaping.unescapeString(res.substring(1, res.length() - 1)));
            }
        } else {
            res = this.match(LINK_DESTINATION);
            if (res != null) {
                return Escaping.normalizeURI(Escaping.unescapeString(res));
            } else {
                return null;
            }
        }
    }

    /**
     * Attempt to parse link title (sans quotes), returning the string or null if no match.
     */
    private String parseLinkTitle() {
        String title = this.match(LINK_TITLE);
        if (title != null) {
            // chop off quotes from title and unescape:
            return Escaping.unescapeString(title.substring(1, title.length() - 1));
        } else {
            return null;
        }
    }

    /**
     * Attempt to parse a link label, returning number of characters parsed.
     */
    private int parseLinkLabel() {
        String m = this.match(LINK_LABEL);
        return m == null ? 0 : m.length();
    }

    /**
     * Attempt to parse an autolink (URL or email in pointy brackets).
     */
    private boolean parseAutolink() {
        String m;
        if ((m = this.match(EMAIL_AUTOLINK)) != null) {
            String dest = m.substring(1, m.length() - 1);
            Link node = new Link(Escaping.normalizeURI("mailto:" + dest), null);
            node.appendChild(new Text(dest));
            appendNode(node);
            return true;
        } else if ((m = this.match(AUTOLINK)) != null) {
            String dest = m.substring(1, m.length() - 1);
            Link node = new Link(Escaping.normalizeURI(dest), null);
            node.appendChild(new Text(dest));
            appendNode(node);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Attempt to parse a raw HTML tag.
     */
    private boolean parseHtmlTag() {
        String m = this.match(HTML_TAG);
        if (m != null) {
            HtmlTag node = new HtmlTag();
            node.setLiteral(m);
            appendNode(node);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Attempt to parse an entity, return Entity object if successful.
     */
    private boolean parseEntity() {
        String m;
        if ((m = this.match(ENTITY_HERE)) != null) {
            appendText(Html5Entities.entityToString(m));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parse a run of ordinary characters, or a single character with a special meaning in markdown, as a plain string.
     */
    private boolean parseString() {
        String m;
        if ((m = this.match(mainPattern)) != null) {
            appendText(m);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Scan a sequence of characters with code delimiterChar, and return information about the number of delimiters
     * and whether they are positioned such that they can open and/or close emphasis or strong emphasis.
     *
     * @return information about delimiter run, or {@code null}
     */
    private DelimiterRun scanDelims(DelimiterProcessor inlineDelimiter) {
        int startPos = this.pos;

        int delimiterCount = 0;
        char delimiterChar = inlineDelimiter.getDelimiterChar();
        while (this.peek() == delimiterChar) {
            delimiterCount++;
            this.pos++;
        }

        if (delimiterCount < inlineDelimiter.getMinDelimiterCount()) {
            this.pos = startPos;
            return null;
        }

        String before = startPos == 0 ? "\n" :
                this.subject.substring(startPos - 1, startPos);

        char charAfter = this.peek();
        String after = charAfter == '\0' ? "\n" :
                String.valueOf(charAfter);

        boolean beforeIsPunctuation = PUNCTUATION.matcher(before).matches();
        boolean beforeIsWhitespace = WHITESPACE_CHAR.matcher(before).matches();
        boolean afterIsWhitespace = WHITESPACE_CHAR.matcher(after).matches();
        boolean afterIsPunctuation = PUNCTUATION.matcher(after).matches();

        boolean leftFlanking = !afterIsWhitespace &&
                !(afterIsPunctuation && !beforeIsWhitespace && !beforeIsPunctuation);
        boolean rightFlanking = !beforeIsWhitespace &&
                !(beforeIsPunctuation && !afterIsWhitespace && !afterIsPunctuation);
        boolean canOpen;
        boolean canClose;
        if (delimiterChar == C_UNDERSCORE) {
            canOpen = leftFlanking && (!rightFlanking || beforeIsPunctuation);
            canClose = rightFlanking && (!leftFlanking || afterIsPunctuation);
        } else {
            canOpen = leftFlanking;
            canClose = rightFlanking;
        }

        this.pos = startPos;
        return new DelimiterRun(delimiterCount, canOpen, canClose);
    }

    private void processDelimiters(Delimiter stackBottom) {
        // find first closer above stackBottom:
        Delimiter closer = this.delimiter;
        while (closer != null && closer.previous != stackBottom) {
            closer = closer.previous;
        }
        // move forward, looking for closers, and handling each
        while (closer != null) {
            if (closer.canClose && delimiterProcessors.containsKey(closer.delimiterChar)) {
                // found delimiter closer. now look back for first matching opener:
                Delimiter opener = closer.previous;
                while (opener != null && opener != stackBottom) {
                    if (opener.delimiterChar == closer.delimiterChar && opener.canOpen) {
                        break;
                    }
                    opener = opener.previous;
                }
                if (opener != null && opener != stackBottom) {
                    DelimiterProcessor delimiterProcessor = delimiterProcessors.get(closer.delimiterChar);

                    int useDelims = delimiterProcessor.getDelimiterUse(opener.numDelims, closer.numDelims);
                    if (useDelims <= 0) {
                        // nope
                        useDelims = 1;
                    }

                    Text openerNode = opener.node;
                    Text closerNode = closer.node;

                    // remove used delimiters from stack elts and inlines
                    opener.numDelims -= useDelims;
                    closer.numDelims -= useDelims;
                    openerNode.setLiteral(
                            openerNode.getLiteral().substring(0,
                                    openerNode.getLiteral().length() - useDelims));
                    closerNode.setLiteral(
                            closerNode.getLiteral().substring(0,
                                    closerNode.getLiteral().length() - useDelims));

                    removeDelimitersBetween(opener, closer);
                    delimiterProcessor.process(openerNode, closerNode, useDelims);

                    // if opener has 0 delims, remove it and the inline
                    if (opener.numDelims == 0) {
                        removeDelimiterAndNode(opener);
                    }

                    if (closer.numDelims == 0) {
                        Delimiter next = closer.next;
                        removeDelimiterAndNode(closer);
                        closer = next;
                    }

                } else {
                    closer = closer.next;
                }

            } else {
                closer = closer.next;
            }

        }

        // remove all delimiters
        while (this.delimiter != stackBottom) {
            removeDelimiterKeepNode(this.delimiter);
        }
    }

    private void removeDelimitersBetween(Delimiter opener, Delimiter closer) {
        Delimiter delimiter = closer.previous;
        while (delimiter != null && delimiter != opener) {
            Delimiter previousDelimiter = delimiter.previous;
            removeDelimiterKeepNode(delimiter);
            delimiter = previousDelimiter;
        }
    }

    /**
     * Remove the delimiter and the corresponding text node. For used delimiters, e.g. `*` in `*foo*`.
     */
    private void removeDelimiterAndNode(Delimiter delim) {
        Text node = delim.node;
        Text previousText = delim.getPreviousNonDelimiterTextNode();
        Text nextText = delim.getNextNonDelimiterTextNode();
        if (previousText != null && nextText != null) {
            // Merge adjacent text nodes
            previousText.setLiteral(previousText.getLiteral() + nextText.getLiteral());
            nextText.unlink();
        }

        node.unlink();
        removeDelimiter(delim);
    }

    /**
     * Remove the delimiter but keep the corresponding node as text. For unused delimiters such as `_` in `foo_bar`.
     */
    private void removeDelimiterKeepNode(Delimiter delim) {
        Text node = delim.node;
        Text previousText = delim.getPreviousNonDelimiterTextNode();
        Text nextText = delim.getNextNonDelimiterTextNode();
        if (previousText != null || nextText != null) {
            // Merge adjacent text nodes into one
            StringBuilder sb = new StringBuilder(node.getLiteral());
            if (previousText != null) {
                sb.insert(0, previousText.getLiteral());
                previousText.unlink();
            }
            if (nextText != null) {
                sb.append(nextText.getLiteral());
                nextText.unlink();
            }
            node.setLiteral(sb.toString());
        }

        removeDelimiter(delim);
    }

    private void removeDelimiter(Delimiter delim) {
        if (delim.previous != null) {
            delim.previous.next = delim.next;
        }
        if (delim.next == null) {
            // top of stack
            this.delimiter = delim.previous;
        } else {
            delim.next.previous = delim.previous;
        }
    }

}
