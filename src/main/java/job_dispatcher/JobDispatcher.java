package job_dispatcher;

import jobs.ScanningJob;
import jobs.ScanningJobType;
import main.Stoppable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class JobDispatcher implements Runnable, Stoppable {

    private final ConcurrentLinkedQueue<ScanningJob> scanningJobs;
    private final BlockingQueue<ScanningJob> fileScanningJobs;
    private final BlockingQueue<ScanningJob> webScanningJobs;

    private volatile boolean forever = true;

    public JobDispatcher(ConcurrentLinkedQueue<ScanningJob> scanningJobs,
                         BlockingQueue<ScanningJob> fileScanningJobs,
                         BlockingQueue<ScanningJob> webScanningJobs) {
        this.scanningJobs = scanningJobs;
        this.fileScanningJobs = fileScanningJobs;
        this.webScanningJobs = webScanningJobs;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.scanningJobs.isEmpty()) {
                ScanningJob scanningJob = this.scanningJobs.poll();

//                System.out.println("Job dispatcher - Scanning job received " + scanningJob.getPath());

                // Break loop and then finish
                if(scanningJob.isPoisonous()) {
                    break;
                }

                // Delegate file scanning job
                if(scanningJob.getType() == ScanningJobType.FILE_SCANNING_JOB) {
//                    System.out.println("File scanning job received: " + scanningJob.getPath());

                    this.fileScanningJobs.add(scanningJob);

                }

                // Delegate web scanning job
                if(scanningJob.getType() == ScanningJobType.WEB_SCANNING_JOB) {
//                    System.out.println("Web scanning job received: " + scanningJob.getPath());

                    this.webScanningJobs.add(scanningJob);
                }
            }

            // Sleep
            try {
                synchronized (this) {
                    wait(200);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Job dispatcher shutting down");
    }

    @Override
    public void stop() {
        this.forever = false;
        this.scanningJobs.add(new ScanningJob());
    }
}
