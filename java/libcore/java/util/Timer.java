package java.util;

public class Timer {
    private final ArrayList<TimerTask> queue = new ArrayList<TimerTask>();
    private final Thread thread;
    private boolean cancelled;

    public Timer() {
        this("Timer", false);
    }

    public Timer(boolean isDaemon) {
        this("Timer", isDaemon);
    }

    public Timer(String name) {
        this(name, false);
    }

    public Timer(String name, boolean isDaemon) {
        thread = new Thread(name) {
            public void run() {
                mainLoop();
            }
        };
        thread.setDaemon(isDaemon);
        thread.start();
    }

    private void mainLoop() {
        while (true) {
            TimerTask task;
            synchronized (queue) {
                while (queue.isEmpty() && !cancelled) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                    }
                }
                if (cancelled) {
                    return;
                }
                task = queue.get(0);
                for (TimerTask t : queue) {
                    if (t.nextExecutionTime < task.nextExecutionTime) {
                        task = t;
                    }
                }
                long now = System.currentTimeMillis();
                if (task.state == TimerTask.CANCELLED) {
                    queue.remove(task);
                    continue;
                }
                if (task.nextExecutionTime > now) {
                    try {
                        queue.wait(task.nextExecutionTime - now);
                    } catch (InterruptedException e) {
                    }
                    continue;
                }
                if (task.period == 0) {
                    queue.remove(task);
                    task.state = TimerTask.EXECUTED;
                } else {
                    task.nextExecutionTime = task.period < 0 ? now - task.period : task.nextExecutionTime + task.period;
                }
            }
            try {
                task.run();
            } catch (Throwable e) {
                synchronized (queue) {
                    cancelled = true;
                    queue.clear();
                }
                throw new RuntimeException(e);
            }
        }
    }

    private void sched(TimerTask task, long time, long period) {
        synchronized (queue) {
            if (cancelled) {
                throw new IllegalStateException("Timer already cancelled.");
            }
            synchronized (task.lock) {
                if (task.state != TimerTask.VIRGIN) {
                    throw new IllegalStateException("Task already scheduled or cancelled");
                }
                task.nextExecutionTime = time;
                task.period = period;
                task.state = TimerTask.SCHEDULED;
            }
            queue.add(task);
            queue.notifyAll();
        }
    }

    public void schedule(TimerTask task, long delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("Negative delay.");
        }
        sched(task, System.currentTimeMillis() + delay, 0);
    }

    public void schedule(TimerTask task, Date time) {
        sched(task, time.getTime(), 0);
    }

    public void schedule(TimerTask task, long delay, long period) {
        if (period <= 0) {
            throw new IllegalArgumentException("Non-positive period.");
        }
        sched(task, System.currentTimeMillis() + delay, -period);
    }

    public void schedule(TimerTask task, Date firstTime, long period) {
        sched(task, firstTime.getTime(), -period);
    }

    public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        if (period <= 0) {
            throw new IllegalArgumentException("Non-positive period.");
        }
        sched(task, System.currentTimeMillis() + delay, period);
    }

    public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
        sched(task, firstTime.getTime(), period);
    }

    public void cancel() {
        synchronized (queue) {
            cancelled = true;
            queue.clear();
            queue.notifyAll();
        }
    }

    public int purge() {
        int result = 0;
        synchronized (queue) {
            Iterator<TimerTask> it = queue.iterator();
            while (it.hasNext()) {
                if (it.next().state == TimerTask.CANCELLED) {
                    it.remove();
                    result++;
                }
            }
        }
        return result;
    }
}
