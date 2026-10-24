package com.localmarts.pos.report.common;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Selects the bundled Latin/Thai font and safely substitutes unsupported glyphs. */
public final class PdfTextSupport {
    private PdfTextSupport() {
    }

    public static List<FontRun> runs(String value, PDType0Font latin, PDType0Font thai) throws IOException {
        String text = value == null ? "" : value;
        List<FontRun> runs = new ArrayList<>();
        PDFont currentFont = null;
        StringBuilder currentText = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Glyph glyph = glyph(codePoint, latin, thai);
            if (currentFont != null && currentFont != glyph.font()) {
                runs.add(new FontRun(currentFont, currentText.toString()));
                currentText.setLength(0);
            }
            currentFont = glyph.font();
            currentText.append(glyph.text());
            offset += Character.charCount(codePoint);
        }
        if (currentFont != null) {
            runs.add(new FontRun(currentFont, currentText.toString()));
        }
        return List.copyOf(runs);
    }

    private static Glyph glyph(int codePoint, PDType0Font latin, PDType0Font thai) throws IOException {
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.THAI) {
            return new Glyph(thai, Character.toString(codePoint));
        }
        if (latin.hasGlyph(codePoint)) {
            return new Glyph(latin, Character.toString(codePoint));
        }
        if (thai.hasGlyph(codePoint)) {
            return new Glyph(thai, Character.toString(codePoint));
        }
        // A missing decorative glyph must not make the whole financial report unavailable.
        return new Glyph(latin, replacement(codePoint));
    }

    private static String replacement(int codePoint) {
        return switch (codePoint) {
            case 0x00A0 -> " ";
            case 0x2018, 0x2019, 0x2032 -> "'";
            case 0x201C, 0x201D, 0x2033 -> "\"";
            case 0x2013, 0x2014, 0x2212 -> "-";
            case 0x2026 -> "...";
            default -> Character.isISOControl(codePoint) ? " " : "?";
        };
    }

    public record FontRun(PDFont font, String text) {
    }

    private record Glyph(PDFont font, String text) {
    }
}
