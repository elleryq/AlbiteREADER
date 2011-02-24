/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.albite.font;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import org.albite.albite.AlbiteMIDlet;

/**
 *
 * @author Albus Dumbledore
 */
public class AlbiteFont {
    protected static final String   INVALID_FILE_ERROR = "ALF file is corrupted.";
    
    protected final String          fontname;
    public    final int             lineHeight;
    public    final int             lineSpacing;
    public    final int             maximumWidth;

    private   final Font            font;

    public final int                spaceWidth;
    public final int                dashWidth;
    public final int                questionWidth;

    private int parseFontSize( String fontname ) {
        int pos = fontname.lastIndexOf( '_' );
        if( pos==-1 )
            return 0;
        String sizeStr = fontname.substring(pos+1);
        return Integer.parseInt(sizeStr);
    }

    private int decideProperSize( int size ) {
        if( size==12 )
            return Font.SIZE_SMALL;
        else if( size==14 )
            return Font.SIZE_MEDIUM;
        else if( size==16 )
            return Font.SIZE_MEDIUM;
        return Font.SIZE_SMALL;
    }

    public AlbiteFont(final String fontname)
            throws AlbiteFontException {

        this.fontname = fontname;

        if( fontname.startsWith("status") ) {
            font = Font.getFont( Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL );
        }
        else if( fontname.startsWith("droid-serif_it")) {
            font = Font.getFont( Font.FACE_PROPORTIONAL, Font.STYLE_ITALIC, decideProperSize(parseFontSize(fontname)) );
        }
        else if( fontname.startsWith("droid-serif")) {
            font = Font.getFont( Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, decideProperSize(parseFontSize(fontname)) );
        }
        else {
            throw new AlbiteFontException(INVALID_FILE_ERROR);
        }

        /*
         * 1 byte for linespacing
         */
        lineSpacing = 2;

        /*
         * 1 byte for lineheight: Characters wouldn't be likely to be
         * more than 127 pixels high, would they?
         */
        lineHeight = font.getHeight();

        /*
        spaceWidth = charWidth(' ');
        dashWidth = charWidth('-');
        questionWidth = charWidth('?');
         */
        spaceWidth = 8;
        dashWidth = 8;
        questionWidth = 8;
        maximumWidth=0;
    }

    public final String getFontname() {
        return fontname;
    }

    public final int charsWidth(
            final char[] c, final int offset, final int length) {

        int res = 0;

        for (int i = offset; i < offset + length; i++) {
            res += charWidth(c[i]);
        }

        //AlbiteMIDlet.LOGGER.log( "charsWidth()=" + res );
        return res;
    }

    public final int charWidth(char c) {
        if( (int)c>0xff )
            return font.charWidth( c );
        else
            return 8;
    }

    public final int charsWidth(final char[] c) {
        return charsWidth(c, 0, c.length);
    }

    public final void drawChars(
            final Graphics g,
            final int color,
            final char[] buffer,
                  int x, final int y,
            final int offset,
            final int length,
            final int limitedWidth) {
        int end = offset+length;
        int c;

        for (int i = offset; i < end; i++) {
            c = buffer[i];
            drawCharFromSystem( g, color, c, x, y );
            x+=charWidth((char)c);
            if( limitedWidth>0 && (x+16)>limitedWidth )
                break;
        }
    }

    public final void drawChars(
            final Graphics g,
            final int color,
            final char[] buffer,
                  int x, final int y,
            final int offset,
            final int length) {
        this.drawChars(g, color, buffer, x, y, offset, length, 0);
    }

    public final void drawChars(
            final Graphics g,
            final int color,
            final char[] buffer,
            final int x, final int y) {
        drawChars(g, color, buffer, x, y, 0, buffer.length);
    }

    private void drawCharFromSystem(
            final Graphics g,
            final int color,
            final int c,
            final int x, final int y ) {
        g.setFont( font );
        g.setColor( color );
        g.drawChar( (char)c, x, y, Graphics.TOP |Graphics.LEFT );
    }

    public final void drawChar(
            final Graphics g,
            final int color,
            final char c,
            final int x, final int y) {

            drawCharFromSystem(g, color, c, x, y);
    }
}