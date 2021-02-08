package Handshake;

import java.io.IOException;
import java.io.InputStream;

public class ReadSocketStream {
    public static String TAG = ReadSocketStream.class.getName();
    public final InputStream SocketStream;

    public ReadSocketStream(InputStream inputStream) {
        this.SocketStream = inputStream;
    }

    public final byte[] ReadData(int len) throws  IOException {
        byte[] buffer = new byte[len];
        int offset = 0;
        while (len > 0) {
            int read = this.SocketStream.read(buffer, offset, len);
            if (read != -1) {
                offset += read;
                len -= read;
            } else {
                return  null;
            }
        }
        return buffer;
    }
}
