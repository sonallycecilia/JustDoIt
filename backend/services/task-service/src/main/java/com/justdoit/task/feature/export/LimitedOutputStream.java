package com.justdoit.task.feature.export;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class LimitedOutputStream extends FilterOutputStream {
    private final long limit;
    private long count;

    LimitedOutputStream(OutputStream delegate, long limit) {
        super(delegate);
        this.limit = limit;
    }

    @Override
    public void write(int value) throws IOException {
        ensureCapacity(1);
        out.write(value);
        count++;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        ensureCapacity(length);
        out.write(bytes, offset, length);
        count += length;
    }

    long count() {
        return count;
    }

    private void ensureCapacity(int additionalBytes) {
        if (additionalBytes > limit - count) {
            throw new ExportLimitExceededException(
                    "Arquivo excedeu o limite configurado de " + limit + " bytes");
        }
    }
}
