package com.danikula.videocache;

/**
 * Indicates any error in work of {@link ProxyCache}.
 *
 * @author Alexey Danilov
 */
public class ProxyCacheException extends Exception {
    public static final int ERROR_TYPE_HTTP = 1;

    private int errorType;
    private int errorCode;

    private static final String LIBRARY_VERSION = ". Version: " + BuildConfig.VERSION_NAME;

    public ProxyCacheException(String message) {
        super(message + LIBRARY_VERSION);
    }

    public ProxyCacheException(int errorType, int errorCode, String msg) {
        super(msg);
        this.errorType = errorType;
        this.errorCode = errorCode;
    }

    public ProxyCacheException(String message, Throwable cause) {
        super(message + LIBRARY_VERSION, cause);
    }

    public ProxyCacheException(Throwable cause) {
        super("No explanation error" + LIBRARY_VERSION, cause);
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public int getErrorType() {
        return errorType;
    }

    public void setErrorType(int errorType) {
        this.errorType = errorType;
    }
}
