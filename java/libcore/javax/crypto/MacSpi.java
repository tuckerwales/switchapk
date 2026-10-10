package javax.crypto;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

public abstract class MacSpi {
    public MacSpi() {
    }

    protected abstract int engineGetMacLength();

    protected abstract void engineInit(Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException;

    protected abstract void engineUpdate(byte input);

    protected abstract void engineUpdate(byte[] input, int offset, int len);

    protected void engineUpdate(ByteBuffer input) {
        if (!input.hasRemaining()) return;
        if (input.hasArray()) {
            engineUpdate(input.array(), input.arrayOffset() + input.position(), input.remaining());
            input.position(input.limit());
            return;
        }
        byte[] b = new byte[input.remaining()];
        input.get(b);
        engineUpdate(b, 0, b.length);
    }

    protected abstract byte[] engineDoFinal();

    protected abstract void engineReset();

    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) return super.clone();
        throw new CloneNotSupportedException();
    }
}
