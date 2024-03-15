package org.lxz.common;

import org.lxz.paxoskv.Paxoskv;

public class Tools {
    public static boolean GE(Paxoskv.BallotNum a, Paxoskv.BallotNum b) {
        if(a.getN() > b.getN()) {
            return true;
        }
        if(a.getN() < b.getN()) {
            return false;
        }
        return a.getProposerId() >= b.getProposerId();
    }

    public static void log(String format, Object... o) {
        System.out.printf((format) + "%n", o);
    }
}
