package org.lxz.common;

public class NotEnoughQuorumException extends Exception {

    public static final NotEnoughQuorumException INSTANCE = new NotEnoughQuorumException("not enough quorum");
    private NotEnoughQuorumException(String message) {
        super(message);
    }
}
