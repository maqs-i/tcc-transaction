package org.mengyun.tcctransaction.spring.repository;

/**
 * Created by hongyuan.wang on 2016/1/26.
 */
public class TransactionIOException extends RuntimeException {

    public TransactionIOException(Throwable e) {
        super(e);
    }
}
