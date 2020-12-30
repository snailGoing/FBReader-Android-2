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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 超链接元素
 *
 * 当遇到超链接开始控制元素，创建此对象；
 * 当遇到超链接结束控制元素，此超链接结束添加新元素
 */
public class ZLTextHyperlink {
    public static final ZLTextHyperlink NO_LINK = new ZLTextHyperlink((byte) 0, null);
    public final byte Type;
    public final String Id;
    /**
     * 指定具有此超链接属性的全部元素索引集合
     */
    private List<Integer> myElementIndexes;

    ZLTextHyperlink(byte type, String id) {
        Type = type;
        Id = id;
    }

    void addElementIndex(int elementIndex) {
        if (myElementIndexes == null) {
            myElementIndexes = new LinkedList<Integer>();
        }
        myElementIndexes.add(elementIndex);
    }

    List<Integer> elementIndexes() {
        return myElementIndexes != null
                ? Collections.unmodifiableList(myElementIndexes)
                : Collections.<Integer>emptyList();
    }
}
