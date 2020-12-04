package org.vimgadgets.linebreak;

public final class LineBreaker {
    public static final char MUSTBREAK = 0;
    public static final char ALLOWBREAK = 1;
    public static final char NOBREAK = 2;
    public static final char INSIDEACHAR = 3;

    static {
        System.loadLibrary("LineBreak-v2");
        init();
    }

    private final String myLanguage;

    public LineBreaker(String lang) {
        myLanguage = lang;
    }

    private static native void init();

    private static native void setLineBreaksForCharArray(char[] data, int offset, int length, String lang, byte[] breaks);

    private static native void setLineBreaksForString(String data, String lang, byte[] breaks);

    public void setLineBreaks(char[] data, int offset, int length, byte[] breaks) {
        setLineBreaksForCharArray(data, offset, length, myLanguage, breaks);
    }

    public void setLineBreaks(String data, byte[] breaks) {
        setLineBreaksForString(data, myLanguage, breaks);
    }
}
