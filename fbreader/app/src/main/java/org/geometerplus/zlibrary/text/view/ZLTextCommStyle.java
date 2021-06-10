package org.geometerplus.zlibrary.text.view;

import org.geometerplus.zlibrary.core.options.ZLIntegerRangeOption;
import org.geometerplus.zlibrary.text.model.ZLTextMetrics;

/**
 * 普通文本样式和多样式的基类
 */
public abstract class ZLTextCommStyle extends ZLTextStyle {

    /**
     * paragraph space data.
     */
    public final static int ParaSpaceArray[] = {4, 5, 6, 7, 8, 9};
    /**
     * mean the ratio of textSize and (-ascent+descent).
     */
    private static double FONT_SIZE_HEIGHT_PERCENT = 0.85333335f;

    public static ZLIntegerRangeOption ParaSpaceOption;

    protected ZLTextCommStyle(ZLTextStyle parent, ZLTextHyperlink hyperlink) {
        super(parent, hyperlink);
        if (ParaSpaceOption == null) {
            ParaSpaceOption = new ZLIntegerRangeOption("Base", "Base:paragraphSpacing", 4, 9, ParaSpaceArray[1]);
        }
    }

    /**
     * Append the space after paragraph according to text height.
     */
    @Override
    public int getSpaceAfter(ZLTextMetrics metrics) {
        return (int)(getFontSize(metrics) / FONT_SIZE_HEIGHT_PERCENT * getParaSpacePercent()  + 0.5f);
    }

    protected float getParaSpacePercent() {
        return ParaSpaceOption.getValue() / 10.0f;
    }
}
