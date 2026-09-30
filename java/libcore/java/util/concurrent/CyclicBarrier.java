package java.util.concurrent;

public class CyclicBarrier {
    private final int parties;
    private final Runnable barrierCommand;
    private int count;
    private int generation;
    private boolean broken;

    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) {
            throw new IllegalArgumentException();
        }
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    public int getParties() {
        return parties;
    }

    public synchronized int await() throws InterruptedException, BrokenBarrierException {
        if (broken) {
            throw new BrokenBarrierException();
        }
        int index = --count;
        int gen = generation;
        if (index == 0) {
            if (barrierCommand != null) {
                barrierCommand.run();
            }
            count = parties;
            generation++;
            notifyAll();
            return 0;
        }
        while (gen == generation && !broken) {
            wait();
        }
        if (broken) {
            throw new BrokenBarrierException();
        }
        return index;
    }

    public synchronized int await(long timeout, TimeUnit unit)
            throws InterruptedException, BrokenBarrierException, TimeoutException {
        return await();
    }

    public synchronized boolean isBroken() {
        return broken;
    }

    public synchronized void reset() {
        broken = true;
        notifyAll();
        broken = false;
        count = parties;
        generation++;
    }

    public synchronized int getNumberWaiting() {
        return parties - count;
    }
}
