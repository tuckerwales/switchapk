package sun.nio.ch;

/** A channel the poll selector can watch. */
interface SelChImpl {
    /** The descriptor, or -1 while there is none (an unconnected, unbound socket). */
    int selFd();

    /** Poll events (1 readable, 2 writable) for interest ops. */
    int pollEvents(int interestOps);

    /** Ready ops from poll results (1 readable, 2 writable, 4 error or hang-up). */
    int readyOps(int revents, int interestOps);
}
