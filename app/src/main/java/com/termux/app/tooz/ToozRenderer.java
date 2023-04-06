package com.termux.app.tooz;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

import java.util.Arrays;

public class ToozRenderer {

    private final Paint mTextPaintTooz;

    /** Tooz */
    public float mTextSizeTooz;
    public float mFontWidthTooz;
    public int mFontLineSpacingTooz;
    public int mFontAscentTooz;
    public int mFontLineSpacingAndAscentTooz;
    public int mFontLineDescentTooz;
    public static final int leftOffsetTooz = 20;

    private final float[] asciiMeasures = new float[127];

    public int toozColor;

    public ToozRenderer(Paint paint) {
        /**Tooz */
        mTextPaintTooz = paint;
        mTextSizeTooz = mTextPaintTooz.getTextSize();
        mFontLineSpacingTooz = (int) Math.ceil(mTextPaintTooz.getFontSpacing());
        mFontAscentTooz = (int) Math.ceil(mTextPaintTooz.ascent());
        mFontLineSpacingAndAscentTooz = mFontLineSpacingTooz + mFontAscentTooz;
        mFontLineDescentTooz = (int) Math.ceil(mTextPaintTooz.descent());
        mFontWidthTooz = mTextPaintTooz.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaintTooz.measureText(sb, 0, 1);
        }

        //Default Tooz text rendering color to green.
        toozColor = 0xff00ff00;
    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection. */
    /** TODO: Tooz */
    public final void renderToTooz(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                                   int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        /**
         * TODO: Figure out a better way to implement this hacky solution/why it's needed
         * Not sure why this has to be scaled up when the canvas width is 400 and the screen is 400 pixels.
         //But for some reason a text width of 400 leaves ~1/1.22 of the glasses screen unused,
         //So we end up treating it as if we sent a bitmap of size 488 instead.
         Update: Fixed, there was a bug in the code where it was using the phone font's width to detect width mismatch,
         and scaling down the font size to compensate when drawing.
         */

        mFontLineSpacingTooz = (int) Math.ceil(mTextPaintTooz.getFontSpacing());
        mFontAscentTooz = (int) Math.ceil(mTextPaintTooz.ascent());
        mFontLineSpacingAndAscentTooz = mFontLineSpacingTooz + mFontAscentTooz;
        mFontWidthTooz = mTextPaintTooz.measureText(" ");

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscentTooz;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacingTooz;
            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaintTooz.measureText(line,
                    currentCharIndex, charsForCodePoint);

                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidthTooz - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column == 0) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }

                        drawTextRunTooz(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = false;
            if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                invertCursorTextColor = true;
            }
            drawTextRunTooz(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }

    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection. */
    /** TODO: Tooz */
    public final void renderRowsToTooz(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                                       int selectionY1, int selectionY2, int selectionX1, int selectionX2, int numRows) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + Integer.min(mEmulator.mRows, numRows);
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        /**
         * TODO: Figure out a better way to implement this hacky solution/why it's needed
         * Not sure why this has to be scaled up when the canvas width is 400 and the screen is 400 pixels.
         //But for some reason a text width of 400 leaves ~1/1.22 of the glasses screen unused,
         //So we end up treating it as if we sent a bitmap of size 488 instead.
         Update: Fixed, there was a bug in the code where it was using the phone font's width to detect width mismatch,
         and scaling down the font size to compensate when drawing.
         */

        mFontLineSpacingTooz = (int) Math.ceil(mTextPaintTooz.getFontSpacing());
        mFontAscentTooz = (int) Math.ceil(mTextPaintTooz.ascent());
        mFontLineSpacingAndAscentTooz = mFontLineSpacingTooz + mFontAscentTooz;
        mFontWidthTooz = mTextPaintTooz.measureText(" ");

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscentTooz;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacingTooz;
            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaintTooz.measureText(line,
                    currentCharIndex, charsForCodePoint);

                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidthTooz - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column == 0) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }

                        drawTextRunTooz(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = false;
            if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                invertCursorTextColor = true;
            }
            drawTextRunTooz(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }

    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection. */
    /** TODO: Tooz */
    public final void renderToToozSingleChar(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                                             int selectionY1, int selectionY2, int selectionX1, int selectionX2, char c, int cursor, int desiredRow, int desiredCol) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        char[] spacesArray = new char[columns];
        Arrays.fill(spacesArray, ' ');
        String spaces = new String(spacesArray);

        mFontLineSpacingTooz = (int) Math.ceil(mTextPaintTooz.getFontSpacing());
        mFontAscentTooz = (int) Math.ceil(mTextPaintTooz.ascent());
        mFontLineSpacingAndAscentTooz = mFontLineSpacingTooz + mFontAscentTooz;
        mFontWidthTooz = mTextPaintTooz.measureText(" ");

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        for (int row = topRow; row < endRow; row++) {
            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];

                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaintTooz.measureText(line,
                    currentCharIndex, charsForCodePoint);

                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidthTooz - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    //if (column == 0) {
                    //     // Skip first column as there is nothing to draw, just record the current style.
                    //    } else {
                    final int columnWidthSinceLastRun = column - lastRunStartColumn;
                    final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                    int cursorColor = (cursor != 0) ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                    boolean invertCursorTextColor = false;
                    if ((cursor != 0) && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                        invertCursorTextColor = true;
                    }
                    if(row == topRow + desiredRow && column == 0 + desiredCol) {
                        Log.w("DRAWING DESIRED ROW COL", String.valueOf(c));
                        /**
                         * TODO:
                         * It seems like measured width is 0 for the first column which is why we aren't drawing anything for the first column.
                         Not sure why this is implemented this way but we can hard code it for now.
                         */

                        drawTextRunToozSingleChar(canvas, new char[]{c}, palette, mFontLineSpacingTooz, 0, columnWidthSinceLastRun,
                            0, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                        //          }

                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

        }
    }
    public float getWidthBeforeTooz(int leftCols) {
        return (leftCols) * mTextPaintTooz.measureText("X");
    }

    public float getHeightBeforeTooz(int topRow, int currRow) {
        float heightOffset = mFontLineSpacingAndAscentTooz;
        heightOffset = 0;

        for (int row = topRow; row < currRow; row++) {
            heightOffset += mFontLineSpacingTooz;
        }
        Log.w("getHeightBeforeTooz", "" + mFontAscentTooz + " " +  mFontLineSpacingAndAscentTooz + " " + mFontLineSpacingTooz + " " + topRow + " " + currRow);
        return heightOffset;

    }

    private void drawTextRunToozSingleChar(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                                           int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                                           long textStyle, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);

        /**
         * Todo: find a better solution.  Right now we are just hard coding that if its the first character in the column use the foreground color as white.
         * */

        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if(runWidthChars == 0) foreColor = 256;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        //float left = startColumn * mFontWidthTooz + leftOffsetTooz;
        int experimentalOffsetForSingleCharacter = 6;
        float left = startColumn * mFontWidthTooz + experimentalOffsetForSingleCharacter;
        float right = left + runWidthColumns * mFontWidthTooz;
        //Log.w("Left 1", ""+left);
        mes = mes / mFontWidthTooz;
        boolean savedMatrix = false;
        /**
         Todo: Figure out a better way than hard coding this for the first column.
         This is a complete hack solution because I don't know why the first column is getting skipped in the first place
         And why it was designed that way for the full screen.  But I guess this works for now.
         */
        if(runWidthChars == 0) {
            mes = 2.0526316f;
        }
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }
        //Log.w("Left 2", ""+left);

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaintTooz.setColor(backColor);
            //canvas.drawRect(left, y - mFontLineSpacingAndAscentTooz, right, y, mTextPaintTooz);
        }

        if (cursor != 0) {
            mTextPaintTooz.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscentTooz - mFontAscentTooz;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            //Log.w("Drawing Cursor", "Test");
            //canvas.drawRect(0, 0, right, y, mTextPaintTooz);
        }
        //Log.w("Left 3", ""+left);

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaintTooz.setFakeBoldText(bold);
            mTextPaintTooz.setUnderlineText(underline);
            mTextPaintTooz.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaintTooz.setStrikeThruText(strikeThrough);
            mTextPaintTooz.setColor(foreColor);

            /**
             Todo: Figure out a better way than hard coding this for the first column.
             This is a complete hack solution because I don't know why the first column is getting skipped in the first place
             And why it was designed that way for the full screen.  But I guess this works for now.
             */
            if(runWidthChars == 0) {
                runWidthChars = 1;
                left = 12.31579f;
            }
            canvas.drawText(text, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscentTooz, mTextPaintTooz);
        }

        if (savedMatrix) canvas.restore();
    }

    private void drawTextRunTooz(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                                 int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                                 long textStyle, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidthTooz + leftOffsetTooz;
        float right = left + runWidthColumns * mFontWidthTooz;

        mes = mes / mFontWidthTooz;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaintTooz.setColor(backColor);
            //canvas.drawRect(left, y - (2 * mFontLineSpacingAndAscentTooz), right, y, mTextPaintTooz);
        }

        if (cursor != 0) {
            mTextPaintTooz.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscentTooz - mFontAscentTooz;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            //canvas.drawRect(left, y - cursorHeight, right, y, mTextPaintTooz);
        }
        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaintTooz.setFakeBoldText(bold);
            mTextPaintTooz.setUnderlineText(underline);
            mTextPaintTooz.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaintTooz.setStrikeThruText(strikeThrough);
            mTextPaintTooz.setColor(foreColor);


            //Set green color if not highlighted.
            if (backColor == palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
                mTextPaintTooz.setColor(toozColor);
            }

            // The text alignment is the default Paint.Align.LEFT.
            //Log.w("Draw", "StartIndex: " + startCharIndex + " runWidth" + runWidthChars + " left: " + left +  " text: " + String.valueOf(text));
            //TODO: Trace the code to figure out why 2*fontlinespacingandascent works.
            canvas.drawText(text, startCharIndex, runWidthChars, left, y - 2 * mFontLineSpacingAndAscentTooz, mTextPaintTooz);
            //canvas.drawText("abcdefghijk", 0, 5, left, y - mFontLineSpacingAndAscent, mTextPaint);

        }

        if (savedMatrix) canvas.restore();
    }

    public void setToozColor(int color) {
        this.toozColor = color;
    }

    public final void renderBoxToTooz(TerminalEmulator mEmulator, Canvas canvas,
                                      int selectionY1, int selectionY2, int selectionX1, int selectionX2, int topRow, int endRow, int leftCol, int rightCol) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        mFontLineSpacingTooz = (int) Math.ceil(mTextPaintTooz.getFontSpacing());
        mFontAscentTooz = (int) Math.ceil(mTextPaintTooz.ascent());
        mFontLineSpacingAndAscentTooz = mFontLineSpacingTooz + mFontAscentTooz;
        mFontWidthTooz = mTextPaintTooz.measureText(" ");

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscentTooz;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacingTooz;
            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaintTooz.measureText(line,
                    currentCharIndex, charsForCodePoint);

                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidthTooz - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column == 0) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }

                        drawTextRunTooz(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = false;
            if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                invertCursorTextColor = true;
            }
            drawTextRunTooz(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }

    }

}