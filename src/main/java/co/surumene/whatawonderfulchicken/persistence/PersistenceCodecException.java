package co.surumene.whatawonderfulchicken.persistence;

public final class PersistenceCodecException extends RuntimeException {
    public PersistenceCodecException(String message) {
        super(message);
    }

    public PersistenceCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
