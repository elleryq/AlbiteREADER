package org.albite.book.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import org.albite.util.archive.Archive;
import org.albite.util.archive.ArchivedFile;
import org.kxml2.io.KXmlParser;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParserException;

//a singleton for performance reasons, mainly memory fragmentation and garbage collection
//syncs not neccessary for this application; may be implemented in future
public class Book {
    final private static String BOOK_TAG                = "book";
    final private static String BOOK_TITLE_TAG          = "title";
    final private static String BOOK_AUTHOR_TAG         = "author";
    final private static String BOOK_DESCRIPTION_TAG    = "description";
    final private static String BOOK_LANGUAGE_TAG       = "language";
    final private static String BOOK_META_TAG           = "meta";

    final private static String CHAPTER_TAG             = "chapter";
    final private static String CHAPTER_SOURCE_ATTRIB   = "src";

    final private static String USERDATA_BOOK_TAG       = "book";
    final private static String USERDATA_BOOKMARK_TAG   = "bookmark";
    final private static String USERDATA_CHAPTER_ATTRIB = "chapter";
    final private static String USERDATA_CHAPTER_TAG    = "chapter";
    final private static String USERDATA_POSITION_ATTRIB= "position";
    final private static String USERDATA_CRC_ATTRIB     = "crc";

    final public static String TEXT_ENCODING            = "UTF-8";

    // Meta Info
    private String  title       = "Untitled";
    private String  author      = "Unknown Author";
    private short   language    = Languages.LANG_UNKNOWN;
    private String  description = "No description";

    private Hashtable meta; //contains various book attribs, e.g. 'fiction', 'for_children', 'prose', etc.
    private Vector    bookmarks;

    //The File
    private Archive archive         = null;
    private FileConnection userfile = null;

    //Chapters
    private Chapter[]   chapters;
    private Chapter   currentChapter;

    //User data; statistics
    private int           timeSpentReading = 0; //in seconds
    private long          timeFromLastCheck; //used from the last time secondsSpentReading was updated

    public final Chapter getCurrentChapter() {
        return currentChapter;
    }

    public final void setCurrentChapter(final Chapter bc) {
        currentChapter = bc;
    }

    public final int getCurrentChapterPosition() {
        return currentChapter.getCurrentPosition();
    }

    public final void setCurrentChapterPos(final int pos) {
        if (pos < 0 || pos >= currentChapter.getTextBufferSize()) {
            throw new IllegalArgumentException("Position is wrong");
        }
        
        currentChapter.setCurrentPosition(pos);
    }

    public void open(String filename) throws IOException, BookException {

        //read file
        archive = new Archive();
        archive.open(filename);

        try {
            //load book description (title, author, etc.)
            loadBookDescriptor();

            //load chapters info (filename + title)
            loadChaptersDescriptor();

            //load user data
            bookmarks = new Vector(10);

            //form user settings filename, i.e. ... .alb -> ... .alx
            int dotpos = filename.lastIndexOf('.');

            char[] alx_chars = new char[dotpos + 5]; //index + .alx + 1
            filename.getChars(0, dotpos +1, alx_chars, 0);
            alx_chars[dotpos+1] = 'a';
            alx_chars[dotpos+2] = 'l';
            alx_chars[dotpos+3] = 'x';

            String alx_filename = new String(alx_chars);

            try {
                userfile = (FileConnection)Connector.open(alx_filename);
                if (!userfile.isDirectory()) {
                    /*
                     * if there is a dir by that name,
                     * the functionality will be disabled
                     *
                     */
                    if (!userfile.exists()) {
                        // create the file if it doesn't exist
                        userfile.create();
//                        System.out.println("User file created");
                    } else {
                        // try to load user settings
                        loadUserData();
                    }
                }
            } catch (IOException e) {
//                System.out.println("Couldn't load user data.");
                userfile.close();
                userfile = null;
            } catch (BookException e) {

                /*
                 * Obviously, the content is wrong. The file's content will be
                 * overwritten as to prevent malformed files from
                 * making it permanently impossible for the user to save date
                 * for a particular book.
                 */
//                System.out.println("Couldn't load user data."
//                        + " File content will be overwritten.");
                e.printStackTrace();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();

        } catch (BookException be) {
            close();
            throw be;
        }
        timeFromLastCheck = System.currentTimeMillis();
    }

    public void close() {

        try {

            archive.close();

            if (userfile != null) {
                userfile.close();
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        System.gc();
    }

    public short getLanguage() {
        return language;
    }

    public void unloadChaptersBuffers() {
        //unload all chapters from memory
        Chapter chap = chapters[0];
        while(chap != null) {
            chap.unload();
            chap = chap.getNextChapter();
        }
    }

    private void loadBookDescriptor() throws BookException, IOException {

        ArchivedFile bookDescriptor = archive.getFile("book.xml");
        if (bookDescriptor == null)
            throw new BookException("Missing book descriptor <book.xml>");

        InputStream in = bookDescriptor.openInputStream();
        meta = new Hashtable(10); //around as much meta info in each book

        KXmlParser parser = null;
        Document doc = null;
        Element root;
        Element kid;

        try {
            parser = new KXmlParser();
            parser.setInput(new InputStreamReader(in));

            doc = new Document();
            doc.parse(parser);
            parser = null;
        } catch (XmlPullParserException xppe) {
            parser = null;
            doc = null;
            throw new BookException(
                    "Book descriptor <book.xml> contains wrong data.");
        }

        root = doc.getRootElement();
        int child_count = root.getChildCount();
        for (int i = 0; i < child_count ; i++ ) {
            if (root.getType(i) != Node.ELEMENT) {
                    continue;
            }

            kid = root.getElement(i);
            if (kid.getName().equals(BOOK_TITLE_TAG))
                title = kid.getText(0);

            if (kid.getName().equals(BOOK_AUTHOR_TAG))
                author = kid.getText(0);

            if (kid.getName().equals(BOOK_DESCRIPTION_TAG))
                description = kid.getText(0);

            if (kid.getName().equals(BOOK_LANGUAGE_TAG))
                try {
                    language = Short.parseShort(kid.getText(0));
                    if (language < 1 || language > Languages.LANGS_COUNT)
                        language = Languages.LANG_UNKNOWN; //set to default
                } catch (NumberFormatException nfe) {
                        language = Languages.LANG_UNKNOWN; //set to default
                }

            if (kid.getName().equals(BOOK_META_TAG)) {
                int meta_count = kid.getChildCount();
                Element metaField;
                for (int m=0; m<meta_count; m++) {
                    if (kid.getType(m) != Node.ELEMENT)
                        continue;
                    metaField = kid.getElement(m);
                    if (metaField.getAttributeCount() > 0)
                        meta.put(
                                metaField.getAttributeValue(0),
                                metaField.getText(0));
                }
            }
        }
    }

    private void loadChaptersDescriptor() throws BookException, IOException {

        ArchivedFile tocDescriptor = archive.getFile("toc.xml");
        if (tocDescriptor == null)
            throw new BookException("Missing TOC descriptor <toc.xml>");

        InputStream in = tocDescriptor.openInputStream();

        KXmlParser parser = null;
        Document doc = null;
        Element root;
        Element kid;

        try {
            parser = new KXmlParser();
            parser.setInput(new InputStreamReader(in));

            doc = new Document();
            doc.parse(parser);
            parser = null;
        } catch (XmlPullParserException xppe) {
            parser = null;
            doc = null;
            throw new BookException(
                    "TOC descriptor <toc.xml> contains wrong data.");
        }

        root = doc.getRootElement();
        int child_count = root.getChildCount();

        String chapterFileName = null;
        String chapterTitle = null;

        Vector chaptersVector = new Vector();
        int currentChapterNumber = 0;
        Chapter prev = null;

        ArchivedFile af = null;

        for (int i = 0; i < child_count ; i++ ) {
            if (root.getType(i) != Node.ELEMENT) {
                    continue;
            }

            kid = root.getElement(i);
            if (kid.getName().equals(CHAPTER_TAG)) {

                currentChapterNumber++;

                chapterFileName = kid.getAttributeValue(
                        KXmlParser.NO_NAMESPACE, CHAPTER_SOURCE_ATTRIB);

                if (chapterFileName == null)
                    throw new BookException(
                            "Invalid TOC descriptor: chapter does not provide"
                            + " src information.");

                if (kid.getChildCount() > 0) {
                    chapterTitle = kid.getText(0);
                    if (chapterTitle == null
                            || chapterTitle.length() == 0
                            || chapterTitle.trim().length() == 0)
                    {
                        chapterTitle = "Chapter #" + (currentChapterNumber);
                    }
                } else {
                    chapterTitle = "Chapter #" + (currentChapterNumber);
                }

                af = archive.getFile(chapterFileName);
                if (af == null)
                    throw new BookException("Chapter #" + currentChapterNumber
                            + " declared, but its file <" + chapterFileName
                            + "> is missing");

                final Chapter cur = new Chapter(af, chapterTitle);
                chaptersVector.addElement(cur);

                if (prev != null) {
                    prev.setNextChapter(cur);
                    cur.setPrevChapter(prev);
                }

                prev = cur;
            }
        }

        if (currentChapterNumber < 1) {
            throw new BookException(
                    "No chapters were found in the TOC descriptor.");
        }

        chapters = new Chapter[chaptersVector.size()];

        for (int i = 0; i < chaptersVector.size(); i ++) {
            chapters[i] = (Chapter) chaptersVector.elementAt(i);
        }

        currentChapter = chapters[0]; //default value
    }

    private void loadUserData() throws BookException, IOException {
        InputStream in = userfile.openInputStream();

        KXmlParser parser = null;
        Document doc = null;
        Element root;
        Element kid;

        try {
            parser = new KXmlParser();
            parser.setInput(new InputStreamReader(in));

            doc = new Document();
            doc.parse(parser);
            parser = null;
        } catch (XmlPullParserException e) {
            parser = null;
            doc = null;
            throw new BookException("Wrong XML data.");
        }

        try {
            /*
             * root element (<book>)
             */
            root = doc.getRootElement();

            int crc = Integer.parseInt(
                    root.getAttributeValue(
                    KXmlParser.NO_NAMESPACE, USERDATA_CRC_ATTRIB));

            int cchapter = Integer.parseInt(
                    root.getAttributeValue(
                    KXmlParser.NO_NAMESPACE, USERDATA_CHAPTER_ATTRIB));

            if (crc != this.archive.getCRC()) {
                throw new BookException("Wrong CRC");
            }

            int childCount = root.getChildCount();

            for (int i = 0; i < childCount ; i++ ) {
                if (root.getType(i) != Node.ELEMENT) {
                    continue;
                }

                kid = root.getElement(i);
                if (kid.getName().equals(USERDATA_BOOKMARK_TAG)) {

                    String text = kid.getText(0);

                    if (text == null) {
                        text = "";
                    }

                    int chapter = Integer.parseInt(
                            kid.getAttributeValue(
                            KXmlParser.NO_NAMESPACE, USERDATA_CHAPTER_ATTRIB));

                    int position = Integer.parseInt(
                            kid.getAttributeValue(
                            KXmlParser.NO_NAMESPACE, USERDATA_POSITION_ATTRIB));

                    if (position < 0) {
                        position = 0;
                    }

                    bookmarks.addElement(
                            new Bookmark(getChapter(chapter),
                            position, text));

                } else if (kid.getName().equals(USERDATA_CHAPTER_TAG)) {


                    int chapter = Integer.parseInt(
                            kid.getAttributeValue(
                            KXmlParser.NO_NAMESPACE, USERDATA_CHAPTER_ATTRIB));

                    int position = Integer.parseInt(
                            kid.getAttributeValue(
                            KXmlParser.NO_NAMESPACE, USERDATA_POSITION_ATTRIB));

                    if (position < 0) {
                        position = 0;
                    }

                    Chapter c = getChapter(chapter);
                    c.setCurrentPosition(position);
                }
            }

            currentChapter = getChapter(cchapter);

        } catch (NullPointerException e) {
            bookmarks.removeAllElements();
            throw new BookException("Missing info (NP Exception).");

        } catch (IllegalArgumentException e) {
            bookmarks.removeAllElements();
            throw new BookException("Malformed int data");

        } catch (RuntimeException e) {
            //document has not root element
            bookmarks.removeAllElements();
            throw new BookException("Wrong data.");

        } finally {
            if (in != null)
                in.close();
        }
    }

    public final void saveUserData() {
        //        Saving book info
        if (chapters != null && //i.e. if any chapters have been read
            userfile != null //i.e. the file is OK for writing
            ) {
            //lets try to save
            try {
                userfile.truncate(0);
                DataOutputStream dout = userfile.openDataOutputStream();
                try {
                    /*
                     * Root element
                     * <book crc="123456789" chapter="3" position="1234">
                     */
                    dout.write("<".getBytes(TEXT_ENCODING));
                    dout.write(USERDATA_BOOK_TAG.getBytes(TEXT_ENCODING));
                    dout.write(" ".getBytes(TEXT_ENCODING));
                    dout.write(USERDATA_CRC_ATTRIB.getBytes(TEXT_ENCODING));
                    dout.write("=\"".getBytes(TEXT_ENCODING));
                    dout.write(Integer.toString(archive.getCRC())
                            .getBytes(TEXT_ENCODING));
                    dout.write("\" ".getBytes(TEXT_ENCODING));
                    dout.write(USERDATA_CHAPTER_ATTRIB.getBytes(TEXT_ENCODING));
                    dout.write("=\"".getBytes(TEXT_ENCODING));
                    dout.write(
                            Integer.toString(getChapterNumber(currentChapter))
                            .getBytes(TEXT_ENCODING));
                    dout.write("\">\n".getBytes(TEXT_ENCODING));

                    /*
                     * current chapter positions
                     * <chapter chapter="3" position="1234" />
                     */
                    for (int i = 0; i < chapters.length; i++) {
                        Chapter c = chapters[i];
                        int n = getChapterNumber(c);
                        int pos = c.getCurrentPosition();

                        dout.write("\t<".getBytes(TEXT_ENCODING));
                        dout.write(USERDATA_CHAPTER_TAG
                                .getBytes(TEXT_ENCODING));
                        dout.write(" ".getBytes(TEXT_ENCODING));
                        dout.write(USERDATA_CHAPTER_ATTRIB
                                .getBytes(TEXT_ENCODING));
                        dout.write("=\"".getBytes(TEXT_ENCODING));
                        dout.write(Integer.toString(n).getBytes(TEXT_ENCODING));
                        dout.write("\" ".getBytes(TEXT_ENCODING));
                        dout.write(USERDATA_POSITION_ATTRIB
                                .getBytes(TEXT_ENCODING));
                        dout.write("=\"".getBytes(TEXT_ENCODING));
                        dout.write(Integer.toString(pos)
                                .getBytes(TEXT_ENCODING));
                        dout.write("\" />\n".getBytes(TEXT_ENCODING));
                    }

                    /*
                     * bookmarks
                     * <bookmark chapter="3" position="1234">Text</bookmark>
                     */
                    for (int i = 0; i < bookmarks.size(); i++) {
                        Bookmark bookmark = (Bookmark)bookmarks.elementAt(i);

                        dout.write("\t<".getBytes(TEXT_ENCODING));
                        dout.write(USERDATA_BOOKMARK_TAG
                                .getBytes(TEXT_ENCODING));
                        dout.write(" ".getBytes(TEXT_ENCODING));
                        dout.write(USERDATA_CHAPTER_ATTRIB
                                .getBytes(TEXT_ENCODING));
                        dout.write("=\"".getBytes(TEXT_ENCODING));
                        dout.write(Integer.toString(getChapterNumber(
                                bookmark.getChapter())
                                ).getBytes(TEXT_ENCODING));
                        dout.write("\" ".getBytes(TEXT_ENCODING));
                        dout.write(USERDATA_POSITION_ATTRIB
                                .getBytes(TEXT_ENCODING));
                        dout.write("=\"".getBytes(TEXT_ENCODING));
                        dout.write(Integer.toString(bookmark.getPosition())
                                .getBytes(TEXT_ENCODING));
                        dout.write("\">".getBytes(TEXT_ENCODING));
                        dout.write(bookmark.getText().getBytes(TEXT_ENCODING));
                        dout.write("</".getBytes(TEXT_ENCODING));
                        dout.write(USERDATA_BOOKMARK_TAG
                                .getBytes(TEXT_ENCODING));
                        dout.write(">\n".getBytes(TEXT_ENCODING));
                    }

                    /*
                     * Close book tag
                     */
                    dout.write("</".getBytes(TEXT_ENCODING));
                    dout.write(USERDATA_BOOK_TAG.getBytes(TEXT_ENCODING));
                    dout.write(">\n".getBytes(TEXT_ENCODING));

                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } finally {
                    dout.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

    }

    public final int getChaptersCount() {
        return chapters.length;
    }

    public final Chapter getChapter(final int number) {

        if (number < 0) {
            return chapters[0];
        }

        if (number > chapters.length - 1) {
            return chapters[chapters.length - 1];
        }

        return chapters[number];
    }

    public final int getChapterNumber(Chapter c) {

        for (int i = 0; i < chapters.length; i ++) {
            if (chapters[i] == c) {
                return (short) i;
            }
        }

        /*
         * Default value
         */
        return 0;
    }

    public Archive getArchive() {
        return archive;
    }

    private void updateTimeSpentReading() {
        timeSpentReading = (int)((System.currentTimeMillis() - timeFromLastCheck)/1000);
        timeFromLastCheck = System.currentTimeMillis();
    }
}