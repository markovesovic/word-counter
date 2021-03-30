package job_dispatcher;

import main.Stoppable;

import java.util.concurrent.ConcurrentLinkedQueue;

public class JobDispatcher implements Runnable, Stoppable {

    private final ConcurrentLinkedQueue<ScanningJob> scanningJobs;

    private volatile boolean forever = true;

    public JobDispatcher(ConcurrentLinkedQueue<ScanningJob> scanningJobs) {
        this.scanningJobs = scanningJobs;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.scanningJobs.isEmpty()) {
                ScanningJob scanningJob = this.scanningJobs.poll();

                if(scanningJob.isPoison()) {
                    break;
                }

                if(scanningJob instanceof FileScanningJob) {
                    System.out.println("Job dispathcer file scanning job with dir: " + ((FileScanningJob) scanningJob).getDirectoryName());
                }
            }
        }
    }

    @Override
    public void stop() {
        this.forever = false;
        this.scanningJobs.add(new FileScanningJob());
    }
}
