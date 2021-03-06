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

public abstract class ZLTextElement {
    protected static final String TAG = ZLTextElement.class.getSimpleName();

    public final static ZLTextElement HSpace = new ZLTextElement() {
        @Override
        public String toString() {
            return "HSpace";
        }
    };

    /**
     * Non Breaking Space.
     * such as: "Mr Green", can't be divided into two parts, you must keep
     * "Mr Green" in the same line.
     */
    public final static ZLTextElement NBSpace = new ZLTextElement() {
        @Override
        public String toString() {
            return "NBSpace";
        }
    };
    public final static ZLTextElement AfterParagraph = new ZLTextElement() {
        @Override
        public String toString() {
            return "AfterParagraph";
        }
    };
    public final static ZLTextElement Indent = new ZLTextElement() {
        @Override
        public String toString() {
            return "Indent";
        }
    };
    public final static ZLTextElement StyleClose = new ZLTextElement() {
        @Override
        public String toString() {
            return "StyleClose";
        }
    };
}
