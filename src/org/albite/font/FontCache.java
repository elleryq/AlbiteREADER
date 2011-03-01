/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.albite.font;

import java.util.Hashtable;
import javax.microedition.lcdui.Font;

/**
 * Class ImageCache provides a number of image
 * methods for image caching
 *
 * @author C. Enrique Ortiz
 */
public final class FontCache{

    private final class Glyph {
        public final char ch;
        public int width;
        public Glyph( char c ) {
            ch = c;
            width = 0;
        }
    }

    /** Singleton instance */
    private static FontCache instance;

    /** In-memory image collection */
    private Hashtable glyphs = new Hashtable();

    /** Number of seconds for cache expiration */
    private static final int EXPIRE_TIME_SECS = 60*60*24; // 1 days

    /**
     * Private Constructor! Creates a new instance of ImageCache
     */
    private FontCache() {
    }

    /**
     * Get singleton instance
     */
    synchronized public static FontCache getInstance() {
        if (instance == null) {
            instance = new FontCache();
        }
        return instance;
    }

    /**
     * Test if glyph is already loaded into memory
     *
     * @param resourceName the name of the resource to test for
     */
    public boolean isGlyphLoaded(char c) {
        return glyphs.get(new Character(c)) != null;
    }

   /**
     * Get glyph width by char.
     *
     * @param   font is the font.
     * @return  the width of the specified character.
     */
    public int getFontCharWidth( final Font font, char c) {
        Glyph glyph = null;
        // Try to load from memory
        glyph = (Glyph)glyphs.get( new Character(c) );
        // If not in memory, load from RMS, network, in that order
        if (glyph == null) {
            // Try to load from RMS
            glyph = new Glyph( c );
            glyph.width = font.charWidth(c);
        }
        return glyph.width; // returns null for network retrievals
    }

}