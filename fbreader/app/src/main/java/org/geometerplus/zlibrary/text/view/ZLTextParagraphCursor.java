/*
 * Copyright (C) 2007-2017 FBReader.ORG Limited <contact@fbreader.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.zlibrary.text.view;

import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.image.ZLImageData;
import org.geometerplus.zlibrary.core.image.ZLImageManager;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.text.model.ZLImageEntry;
import org.geometerplus.zlibrary.text.model.ZLTextMark;
import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.model.ZLTextOtherStyleEntry;
import org.geometerplus.zlibrary.text.model.ZLTextParagraph;
import org.geometerplus.zlibrary.text.model.ZLTextStyleEntry;
import org.vimgadgets.linebreak.LineBreaker;

import java.util.ArrayList;
import java.util.List;

/**
 * The paragraph data which will be filled according to the specified format.
 */
public final class ZLTextParagraphCursor {

    private static final char[] SPACE_ARRAY = {' '};
    /**
     * The index of this paragraph.
     */
    public final int Index;
    /**
     * The model of text which provide all the paragraphs info.
     */
    public final ZLTextModel Model;
    /**
     * Create a paragraph of the specified index.
     */
    final CursorManager CursorManager;

    /**
     * Used to storage all the elements of this paragraph.
     */
    private final ArrayList<ZLTextElement> myElements = new ArrayList<ZLTextElement>();

    public ZLTextParagraphCursor(ZLTextModel model, int index) {
        this(new CursorManager(model, null), model, index);
    }

    ZLTextParagraphCursor(CursorManager cManager, ZLTextModel model, int index) {
        CursorManager = cManager;
        Model = model;
        Index = Math.min(index, model.getParagraphsNumber() - 1);
        fill();
    }

    /**
     * Fill the paragraph data.
     */
    void fill() {
        ZLTextParagraph paragraph = Model.getParagraph(Index);
        switch (paragraph.getKind()) {
            case ZLTextParagraph.Kind.TEXT_PARAGRAPH:
                // major implementation to fill data.
                new Processor(paragraph, CursorManager.ExtensionManager, new LineBreaker(Model.getLanguage()), Model.getMarks(), Index, myElements).fill();
                break;
            case ZLTextParagraph.Kind.EMPTY_LINE_PARAGRAPH:
                myElements.add(new ZLTextWord(SPACE_ARRAY, 0, 1, 0));
                break;
            case ZLTextParagraph.Kind.ENCRYPTED_SECTION_PARAGRAPH: {
                final ZLTextStyleEntry entry = new ZLTextOtherStyleEntry();
                entry.setFontModifier(ZLTextStyleEntry.FontModifier.FONT_MODIFIER_BOLD, true);
                myElements.add(new ZLTextStyleElement(entry));
                myElements.add(new ZLTextWord(ZLResource.resource("drm").getResource("encryptedSection").getValue(), 0));
                break;
            }
            default:
                break;
        }
    }

    void clear() {
        myElements.clear();
    }

    public boolean isFirst() {
        return Index == 0;
    }

    public boolean isLast() {
        return Index + 1 >= Model.getParagraphsNumber();
    }

    public boolean isLikeEndOfSection() {
        switch (Model.getParagraph(Index).getKind()) {
            case ZLTextParagraph.Kind.END_OF_SECTION_PARAGRAPH:
            case ZLTextParagraph.Kind.PSEUDO_END_OF_SECTION_PARAGRAPH:
                return true;
            default:
                return false;
        }
    }

    public boolean isEndOfSection() {
        return Model.getParagraph(Index).getKind() == ZLTextParagraph.Kind.END_OF_SECTION_PARAGRAPH;
    }

    int getParagraphLength() {
        return myElements.size();
    }

    public ZLTextParagraphCursor previous() {
        return isFirst() ? null : CursorManager.get(Index - 1);
    }

    public ZLTextParagraphCursor next() {
        return isLast() ? null : CursorManager.get(Index + 1);
    }

    ZLTextElement getElement(int index) {
        try {
            return myElements.get(index);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    ZLTextParagraph getParagraph() {
        return Model.getParagraph(Index);
    }

    @Override
    public String toString() {
        return "ZLTextParagraphCursor [" + Index + " (0.." + myElements.size() + ")]";
    }

    private static final class Processor {
        private static final int NO_SPACE = 0;
        private static final int SPACE = 1;
        private static final int NON_BREAKABLE_SPACE = 2;
        private static byte[] ourBreaks = new byte[1024];
        private final ZLTextParagraph myParagraph;
        private final ExtensionElementManager myExtManager;
        private final LineBreaker myLineBreaker;
        private final ArrayList<ZLTextElement> myElements;
        private final List<ZLTextMark> myMarks;
        private int myOffset;
        private int myFirstMark;
        private int myLastMark;

        private Processor(ZLTextParagraph paragraph, ExtensionElementManager extManager, LineBreaker lineBreaker, List<ZLTextMark> marks, int paragraphIndex, ArrayList<ZLTextElement> elements) {
            myExtManager = extManager;
            myParagraph = paragraph;
            myLineBreaker = lineBreaker;
            myElements = elements;
            myMarks = marks;

            // Here is the processing of search results.
            final ZLTextMark mark = new ZLTextMark(paragraphIndex, 0, 0);
            int i;
            for (i = 0; i < myMarks.size(); i++) {
                // get the first mark (included) after the beginning element of this paragraph
                if (((ZLTextMark) myMarks.get(i)).compareTo(mark) >= 0) {
                    break;
                }
            }
            // save first mark location.
            myFirstMark = i;
            myLastMark = myFirstMark;
            // loop to find the last mark in this paragraph.
            for (; myLastMark != myMarks.size() && ((ZLTextMark) myMarks.get(myLastMark)).ParagraphIndex == paragraphIndex; myLastMark++)
                ;
            myOffset = 0;
        }

        void fill() {
            int hyperlinkDepth = 0;
            ZLTextHyperlink hyperlink = null;

            final ArrayList<ZLTextElement> elements = myElements;
            // ZLTextParagraph can provide the iterator and loop every element of this paragraph.
            for (ZLTextParagraph.EntryIterator it = myParagraph.iterator(); it.next(); ) {
                switch (it.getType()) {
                    case ZLTextParagraph.Entry.TEXT:
                        processTextEntry(it.getTextData(), it.getTextOffset(), it.getTextLength(), hyperlink);
                        break;
                    case ZLTextParagraph.Entry.CONTROL:
                        if (hyperlink != null) {
                            hyperlinkDepth += it.getControlIsStart() ? 1 : -1;
                            if (hyperlinkDepth == 0) {
                                hyperlink = null;
                            }
                        }
                        // create control element and add it to list.
                        elements.add(ZLTextControlElement.get(it.getControlKind(), it.getControlIsStart()));
                        break;
                    case ZLTextParagraph.Entry.HYPERLINK_CONTROL: {
                        final byte hyperlinkType = it.getHyperlinkType();
                        if (hyperlinkType != 0) {
                            final ZLTextHyperlinkControlElement control =
                                    new ZLTextHyperlinkControlElement(
                                            it.getControlKind(), hyperlinkType, it.getHyperlinkId()
                                    );
                            elements.add(control);
                            hyperlink = control.Hyperlink;
                            hyperlinkDepth = 1;
                        }
                        break;
                    }
                    case ZLTextParagraph.Entry.IMAGE:
                        final ZLImageEntry imageEntry = it.getImageEntry();
                        final ZLImage image = imageEntry.getImage();
                        if (image != null) {
                            ZLImageData data = ZLImageManager.Instance().getImageData(image);
                            if (data != null) {
                                if (hyperlink != null) {
                                    hyperlink.addElementIndex(elements.size());
                                }
                                elements.add(new ZLTextImageElement(imageEntry.Id, data, image.getURI(), imageEntry.IsCover));
                            }
                        }
                        break;
                    case ZLTextParagraph.Entry.AUDIO:
                        break;
                    case ZLTextParagraph.Entry.VIDEO:
                        elements.add(new ZLTextVideoElement(it.getVideoEntry().sources()));
                        break;
                    case ZLTextParagraph.Entry.EXTENSION:
                        if (myExtManager != null) {
                            elements.addAll(myExtManager.getElements(it.getExtensionEntry()));
                        }
                        break;
                    case ZLTextParagraph.Entry.STYLE_CSS:
                    case ZLTextParagraph.Entry.STYLE_OTHER:
                        elements.add(new ZLTextStyleElement(it.getStyleEntry()));
                        break;
                    case ZLTextParagraph.Entry.STYLE_CLOSE:
                        elements.add(ZLTextElement.StyleClose);
                        break;
                    case ZLTextParagraph.Entry.FIXED_HSPACE:
                        elements.add(ZLTextFixedHSpaceElement.getElement(it.getFixedHSpaceLength()));
                        break;
                    default:
                        break;
                }
            }
        }

        private void processTextEntry(final char[] data, final int offset, final int length, ZLTextHyperlink hyperlink) {
            if (length != 0) {
                if (ourBreaks.length < length) {
                    ourBreaks = new byte[length];
                }
                final byte[] breaks = ourBreaks;
                // make a line-break to the input length char array.
                myLineBreaker.setLineBreaks(data, offset, length, breaks);

                final ZLTextElement hSpace = ZLTextElement.HSpace;
                final ZLTextElement nbSpace = ZLTextElement.NBSpace;
                final ArrayList<ZLTextElement> elements = myElements;
                char ch = 0;
                char previousChar = 0;
                int spaceState = NO_SPACE;
                int wordStart = 0;
                for (int index = 0; index < length; ++index) {
                    previousChar = ch;
                    ch = data[offset + index];
                    if (Character.isWhitespace(ch)) {
                        if (index > 0 && spaceState == NO_SPACE) {
                            addWord(data, offset + wordStart, index - wordStart, myOffset + wordStart, hyperlink);
                        }
                        spaceState = SPACE;
                    } else if (Character.isSpaceChar(ch)) {
                        if (index > 0 && spaceState == NO_SPACE) {
                            addWord(data, offset + wordStart, index - wordStart, myOffset + wordStart, hyperlink);
                        }
                        elements.add(nbSpace);
                        if (spaceState != SPACE) {
                            spaceState = NON_BREAKABLE_SPACE;
                        }
                    } else {
                        switch (spaceState) {
                            case SPACE:
                                //if (breaks[index - 1] == LineBreak.NOBREAK || previousChar == '-') {
                                //}
                                elements.add(hSpace);
                                wordStart = index;
                                break;
                            case NON_BREAKABLE_SPACE:
                                wordStart = index;
                                break;
                            case NO_SPACE:
                                if (index > 0 &&
                                        breaks[index - 1] != LineBreaker.NOBREAK &&
                                        previousChar != '-' &&
                                        index != wordStart) {
                                    addWord(data, offset + wordStart, index - wordStart, myOffset + wordStart, hyperlink);
                                    wordStart = index;
                                }
                                break;
                            default:
                                break;
                        }
                        spaceState = NO_SPACE;
                    }
                }
                switch (spaceState) {
                    case SPACE:
                        elements.add(hSpace);
                        break;
                    case NON_BREAKABLE_SPACE:
                        elements.add(nbSpace);
                        break;
                    case NO_SPACE:
                        addWord(data, offset + wordStart, length - wordStart, myOffset + wordStart, hyperlink);
                        break;
                    default:
                        break;
                }
                myOffset += length;
            }
        }

        /**
         * Add a new ZLTextWord to list.
         * If a hyperlink contains this word, add this word index to the hyperlink.
         * Besides, if the search marks contains this word, create a new mark.
         *
         * @param data The ncache files' char array source.
         * @param offset The start offset in the array of data.
         * @param len The words' length.
         * @param paragraphOffset The start offset in this paragraph.
         * @param hyperlink The hyperlink value, maybe {@link ZLTextHyperlink#NO_LINK}.
         */
        private final void addWord(char[] data, int offset, int len, int paragraphOffset, ZLTextHyperlink hyperlink) {
            ZLTextWord word = new ZLTextWord(data, offset, len, paragraphOffset);
            for (int i = myFirstMark; i < myLastMark; ++i) {
                final ZLTextMark mark = (ZLTextMark) myMarks.get(i);
                if ((mark.Offset < paragraphOffset + len) && (mark.Offset + mark.Length > paragraphOffset)) {
                    word.addMark(mark.Offset - paragraphOffset, mark.Length);
                }
            }
            if (hyperlink != null) {
                hyperlink.addElementIndex(myElements.size());
            }
            myElements.add(word);
        }
    }
}
