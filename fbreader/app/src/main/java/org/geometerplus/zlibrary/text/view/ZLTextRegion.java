/*
 * Copyright (C) 2009-2017 FBReader.ORG Limited <contact@fbreader.org>
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

import org.geometerplus.fbreader.fbreader.FBView;
import org.geometerplus.zlibrary.core.view.Hull;

import java.util.ArrayList;
import java.util.List;

/**
 * 页面的元素区域类
 *
 * 一般情形，一个文本就是一个区域。但是对于超链接可能包含多个文本，
 * 此时，当超链接元素添加后，如果后面的文本元素范围被超链接包含，
 * 那么，后面的文本元素就不会创建一个新的 ZLTextRegion 对象，而是
 * 在超链接的 ZLTextRegion 对象上执行扩充 extend()。
 * Reference {@link ZLTextElementAreaVector#add(ZLTextElementArea)}使用
 */
public final class ZLTextRegion {
    public static Filter AnyRegionFilter = new Filter() {
        public boolean accepts(ZLTextRegion region) {
            return true;
        }
    };
    public static Filter HyperlinkFilter = new Filter() {
        public boolean accepts(ZLTextRegion region) {
            return region.getSoul() instanceof ZLTextHyperlinkRegionSoul;
        }
    };
    public static Filter VideoFilter = new Filter() {
        public boolean accepts(ZLTextRegion region) {
            return region.getSoul() instanceof ZLTextVideoRegionSoul;
        }
    };
    public static Filter ExtensionFilter = new Filter() {
        public boolean accepts(ZLTextRegion region) {
            return region.getSoul() instanceof ExtensionRegionSoul;
        }
    };
    public static Filter ImageOrHyperlinkFilter = new Filter() {
        public boolean accepts(ZLTextRegion region) {
            final Soul soul = region.getSoul();
            return
                    soul instanceof ZLTextImageRegionSoul ||
                            soul instanceof ZLTextHyperlinkRegionSoul;
        }
    };
    private final Soul mySoul;
    // this field must be accessed in synchronized context only
    private final List<ZLTextElementArea> myAreaList;
    private final int myFromIndex;
    private ZLTextElementArea[] myAreas;
    private int myToIndex;
    private Hull myHull;
    private Hull myHull0; // convex hull for left page column

    ZLTextRegion(Soul soul, List<ZLTextElementArea> list, int fromIndex) {
        mySoul = soul;
        myAreaList = list;
        myFromIndex = fromIndex;
        myToIndex = fromIndex + 1;
    }

    void extend() {
        ++myToIndex;
        myHull = null;
    }

    public Soul getSoul() {
        return mySoul;
    }

    ZLTextElementArea[] textAreas() {
        if (myAreas == null || myAreas.length != myToIndex - myFromIndex) {
            synchronized (myAreaList) {
                myAreas = new ZLTextElementArea[myToIndex - myFromIndex];
                for (int i = 0; i < myAreas.length; ++i) {
                    myAreas[i] = myAreaList.get(i + myFromIndex);
                }
            }
        }
        return myAreas;
    }

    Hull hull() {
        if (myHull == null) {
            myHull = HullUtil.hull(textAreas());
        }
        return myHull;
    }

    Hull hull0() {
        if (myHull0 == null) {
            final List<ZLTextElementArea> column0 = new ArrayList<ZLTextElementArea>();
            for (ZLTextElementArea a : textAreas()) {
                if (a.ColumnIndex == 0) {
                    column0.add(a);
                }
            }
            myHull0 = HullUtil.hull(column0);
        }
        return myHull0;
    }

    ZLTextElementArea getFirstArea() {
        return textAreas()[0];
    }

    ZLTextElementArea getLastArea() {
        final ZLTextElementArea[] areas = textAreas();
        return areas[areas.length - 1];
    }

    public int getLeft() {
        int left = Integer.MAX_VALUE;
        for (ZLTextElementArea area : textAreas()) {
            left = Math.min(area.XStart, left);
        }
        return left;
    }

    public int getRight() {
        int right = Integer.MIN_VALUE;
        for (ZLTextElementArea area : textAreas()) {
            right = Math.max(area.XEnd, right);
        }
        return right;
    }

    public int getTop() {
        return getFirstArea().YStart;
    }

    public int getBottom() {
        return getLastArea().YEnd;
    }

    int distanceTo(int x, int y) {
        return hull().distanceTo(x, y);
    }

    boolean isBefore(int x, int y, int columnIndex) {
        switch (columnIndex) {
            default:
            case -1:
                return hull().isBefore(x, y);
            case 0: {
                int count0 = 0;
                int count1 = 0;
                for (ZLTextElementArea area : textAreas()) {
                    if (area.ColumnIndex == 0) {
                        ++count0;
                    } else {
                        ++count1;
                    }
                }
                if (count0 == 0) {
                    return false;
                } else if (count1 == 0) {
                    return hull().isBefore(x, y);
                } else {
                    return hull0().isBefore(x, y);
                }
            }
            case 1:
                for (ZLTextElementArea area : textAreas()) {
                    if (area.ColumnIndex == 0) {
                        return true;
                    }
                }
                return hull().isBefore(x, y);
        }
    }

    boolean isAtRightOf(ZLTextRegion other) {
        return
                other == null ||
                        getFirstArea().XStart >= other.getLastArea().XEnd;
    }

    boolean isAtLeftOf(ZLTextRegion other) {
        return other == null || other.isAtRightOf(this);
    }

    boolean isUnder(ZLTextRegion other) {
        return
                other == null ||
                        getFirstArea().YStart >= other.getLastArea().YEnd;
    }

    boolean isOver(ZLTextRegion other) {
        return other == null || other.isUnder(this);
    }

    boolean isExactlyUnder(ZLTextRegion other) {
        if (other == null) {
            return true;
        }
        if (!isUnder(other)) {
            return false;
        }
        final ZLTextElementArea[] areas0 = textAreas();
        final ZLTextElementArea[] areas1 = other.textAreas();
        for (ZLTextElementArea i : areas0) {
            for (ZLTextElementArea j : areas1) {
                if (i.XStart <= j.XEnd && j.XStart <= i.XEnd) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isExactlyOver(ZLTextRegion other) {
        return other == null || other.isExactlyUnder(this);
    }

    public boolean isVerticallyAligned() {
        for (ZLTextElementArea area : textAreas()) {
            if (!area.Style.isVerticallyAligned()) {
                return false;
            }
        }
        return true;
    }

    public static interface Filter {
        boolean accepts(ZLTextRegion region);
    }

    /**
     * 作用于页面区域点击时，查找满足指定匹配条件的区域
     * 作为 {@link ZLTextRegion} 元素区域的成员
     *
     * Soul 指定的区域的元素范围[start, end]，对于超链接可能包含多个
     * 元素，那么，end 索引应该是对后一个元素的位置。
     *
     * 可查阅 {@link FBView#onFingerSingleTap(int, int)} 中的使用
     * {@link FBView#findRegion(int, int, int, Filter)} 参数 Filter 为指定的过滤条件
     */
    public static abstract class Soul implements Comparable<Soul> {
        final int ParagraphIndex;
        final int StartElementIndex;
        final int EndElementIndex;

        protected Soul(int paragraphIndex, int startElementIndex, int endElementIndex) {
            ParagraphIndex = paragraphIndex;
            StartElementIndex = startElementIndex;
            EndElementIndex = endElementIndex;
        }

        final boolean accepts(ZLTextElementArea area) {
            return compareTo(area) == 0;
        }

        @Override
        public final boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof Soul)) {
                return false;
            }
            final Soul soul = (Soul) other;
            return
                    ParagraphIndex == soul.ParagraphIndex &&
                            StartElementIndex == soul.StartElementIndex &&
                            EndElementIndex == soul.EndElementIndex;
        }

        public final int compareTo(Soul soul) {
            if (ParagraphIndex != soul.ParagraphIndex) {
                return ParagraphIndex < soul.ParagraphIndex ? -1 : 1;
            }
            if (EndElementIndex < soul.StartElementIndex) {
                return -1;
            }
            if (StartElementIndex > soul.EndElementIndex) {
                return 1;
            }
            return 0;
        }

        public final int compareTo(ZLTextElementArea area) {
            if (ParagraphIndex != area.ParagraphIndex) {
                return ParagraphIndex < area.ParagraphIndex ? -1 : 1;
            }
            if (EndElementIndex < area.ElementIndex) {
                return -1;
            }
            if (StartElementIndex > area.ElementIndex) {
                return 1;
            }
            return 0;
        }

        public final int compareTo(ZLTextPosition position) {
            final int ppi = position.getParagraphIndex();
            if (ParagraphIndex != ppi) {
                return ParagraphIndex < ppi ? -1 : 1;
            }
            final int pei = position.getElementIndex();
            if (EndElementIndex < pei) {
                return -1;
            }
            if (StartElementIndex > pei) {
                return 1;
            }
            return 0;
        }
    }
}
