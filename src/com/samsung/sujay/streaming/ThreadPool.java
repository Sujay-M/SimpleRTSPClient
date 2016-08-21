package com.samsung.sujay.streaming;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by sujay on 21/8/16.
 */
public class ThreadPool
{
    private static final int NUMTHREADS = 5;
    private static ThreadPool INSTANCE;
    private ExecutorService senderThreads;

    private ThreadPool(int numThreads)
    {
        senderThreads = Executors.newFixedThreadPool(numThreads);
    }

    public static ThreadPool getINSTANCE()
    {
        if (INSTANCE == null)
            INSTANCE = new ThreadPool(NUMTHREADS);
        return INSTANCE;
    }

    public void execute(Runnable runnable)
    {
        senderThreads.execute(runnable);
    }

    public void shutDown()
    {
        senderThreads.shutdown();
    }

}
