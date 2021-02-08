package Handshake;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class WriteSocketStream extends FilterOutputStream {

    public WriteSocketStream(OutputStream outputStream) {
        super(outputStream);
    }

    @Override // java.io.OutputStream, java.io.FilterOutputStream
    public void write(int i) throws IOException {
        write(new byte[]{(byte) i});
    }

    @Override // java.io.OutputStream, java.io.FilterOutputStream
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override // java.io.OutputStream, java.io.FilterOutputStream
    public void write(byte[] data, int offset, int len) throws IOException {
        if (len < 16777216) {
            out.write(HandshakeUtil.GenerateDataHead(len));
            out.write(data, offset, len);
            out.flush();
        }
    }

}
