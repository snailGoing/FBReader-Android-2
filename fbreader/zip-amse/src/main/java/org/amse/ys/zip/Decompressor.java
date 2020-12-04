package org.amse.ys.zip;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public abstract class Decompressor {
    private static Queue<DeflatingDecompressor> ourDeflators = new LinkedList<DeflatingDecompressor>();

    public Decompressor(MyBufferedInputStream is, LocalFileHeader header) {
    }

    protected Decompressor() {
    }

    static void storeDecompressor(Decompressor decompressor) {
        if (decompressor instanceof DeflatingDecompressor) {
            synchronized (ourDeflators) {
                ourDeflators.add((DeflatingDecompressor) decompressor);
            }
        }
    }

    static Decompressor init(MyBufferedInputStream is, LocalFileHeader header) throws IOException {
        switch (header.CompressionMethod) {
            case 0:
                return new NoCompressionDecompressor(is, header);
            case 8:
                synchronized (ourDeflators) {
                    if (!ourDeflators.isEmpty()) {
                        DeflatingDecompressor decompressor = ourDeflators.poll();
                        decompressor.reset(is, header);
                        return decompressor;
                    }
                }
                return new DeflatingDecompressor(is, header);
            default:
                throw new ZipException("Unsupported method of compression");
        }
    }

    /**
     * byte b[] -- target buffer for bytes; might be null
     */
    public abstract int read(byte b[], int off, int len) throws IOException;

    public abstract int read() throws IOException;

    public int available() throws IOException {
        return -1;
    }
}
