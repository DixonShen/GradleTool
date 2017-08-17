/**
 * 监视线程，每个build任务有相应的监视线程
 * 当build任务超过200s未结束时，强制结束任务，build的log信息打印到相应文件
 * Created by z003r98d on 6/27/2017.
 */
public class TimeoutMonitor extends Thread {

    private GradleUtil.BuildTask blinker;

    public TimeoutMonitor(GradleUtil.BuildTask t) {
        super();
        this.blinker = t;
    }

    @Override
    public void run(){
        while (blinker != null) {
            try {
                if ((System.currentTimeMillis() - blinker.startTime) > (GradleUtil.timeout * 1000)) {
                    blinker.stopTask();
                    return;
                }
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void stopMonitor(){
        this.blinker = null;
    }
}
