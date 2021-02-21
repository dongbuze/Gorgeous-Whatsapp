package Util;

import java.util.concurrent.LinkedTransferQueue;

//抛到looper中的task，会在一个线程中处理，可以在任意线程抛任务，会在同一个线程处理
public class GorgeoesLooper {
    public void Init() {
        //启动线程循环读取task并执行
        runnableThread_ = new Thread(()->{
            while (true) {
                try {
                    Runnable runnable = tasks_.take();
                    runnable.run();
                }
                catch (Exception e){
                }
            }
        });
        runnableThread_.start();
    }

    public void UnInit() {
        if (null != runnableThread_) {
            tasks_.put(null);
            try {
                runnableThread_.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            runnableThread_ = null;
        }
    }

    public void CheckThread() {
        if(runnableThread_.getId() == Thread.currentThread().getId()) {
            try {
                throw new Exception("call thread is not in looper thread");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void PostTask(Runnable runnable) {
        tasks_.add(runnable);
    }

    public static GorgeoesLooper Instance() {
        return s_GorgeoesLooper;
    }

    private LinkedTransferQueue<Runnable> tasks_ = new LinkedTransferQueue<>();
    private Thread runnableThread_;
    private static GorgeoesLooper s_GorgeoesLooper = new GorgeoesLooper();
    private GorgeoesLooper() {
    }
}
