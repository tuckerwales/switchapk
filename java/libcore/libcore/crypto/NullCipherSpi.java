package libcore.crypto;

import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.CipherSpi;
import javax.crypto.ShortBufferException;

/** The identity transformation behind javax.crypto.NullCipher. */
public final class NullCipherSpi extends CipherSpi {
    protected void engineSetMode(String mode) {
    }

    protected void engineSetPadding(String padding) {
    }

    protected int engineGetBlockSize() {
        return 1;
    }

    protected int engineGetOutputSize(int inputLen) {
        return inputLen;
    }

    protected byte[] engineGetIV() {
        return new byte[8];
    }

    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    protected void engineInit(int opmode, Key key, SecureRandom random) {
    }

    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random) {
    }

    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random) {
    }

    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        if (input == null) return null;
        byte[] out = new byte[inputLen];
        System.arraycopy(input, inputOffset, out, 0, inputLen);
        return out;
    }

    protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException {
        if (input == null) return 0;
        if (output.length - outputOffset < inputLen) throw new ShortBufferException("output buffer too small");
        System.arraycopy(input, inputOffset, output, outputOffset, inputLen);
        return inputLen;
    }

    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) {
        byte[] out = engineUpdate(input, inputOffset, inputLen);
        return out != null ? out : new byte[0];
    }

    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException {
        return engineUpdate(input, inputOffset, inputLen, output, outputOffset);
    }
}
