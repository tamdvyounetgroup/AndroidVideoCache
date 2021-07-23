package com.danikula.videocache;

public class HttpErrorException extends Exception {
    private int errorCode;
    public HttpErrorException(int errorCode, String msg) {
        super(msg);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
}
