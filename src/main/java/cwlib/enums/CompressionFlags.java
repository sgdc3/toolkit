package cwlib.enums;

/**
 * Flags used during serialization to determine if any
 * data types should be compressed.
 */
public final class CompressionFlags {
    /**
     * No compression is used at all.
     */
    public static final byte USE_NO_COMPRESSION = 0;

    /**
     * All 32-bit and 64-bit integer data types are serialized as uleb128
     */
    public static final byte USE_COMPRESSED_INTEGERS = 1;

    /**
     * Arrays are serialized in variable length byte arrays
     */
    public static final byte USE_COMPRESSED_VECTORS = 2;

    /**
     * 4x4 matrices will have a short flag for which
     * components are serialized.
     */
    public static final byte USE_COMPRESSED_MATRICES = 4;

    /**
     * Uses all of the above compression techniques.
     */
    public static final byte USE_ALL_COMPRESSION = 7;
}
