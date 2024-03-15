package org.lxz.impl;

import org.lxz.paxoskv.Paxoskv;

import java.util.concurrent.locks.ReentrantLock;

public class Version {
    public ReentrantLock lock;
    public Paxoskv.Acceptor acceptor;

    public Version(Paxoskv.Acceptor acceptor) {
        this.acceptor = acceptor;
        this.lock = new ReentrantLock();
    }
}
