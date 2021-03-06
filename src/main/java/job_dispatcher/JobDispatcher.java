package job_dispatcher;

import jobs.FileScanningJob;
import jobs.Job;
import jobs.WebScanningJob;
import main.Stoppable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class JobDispatcher implements Runnable, Stoppable {

    private final BlockingQueue<Job> jobs;
    private final BlockingQueue<FileScanningJob> fileScanningJobs;
    private final BlockingQueue<WebScanningJob> webScanningJobs;

    private volatile boolean forever = true;

    public JobDispatcher(BlockingQueue<Job> jobs,
                         BlockingQueue<FileScanningJob> fileScanningJobs,
                         BlockingQueue<WebScanningJob> webScanningJobs) {
        this.jobs = jobs;
        this.fileScanningJobs = fileScanningJobs;
        this.webScanningJobs = webScanningJobs;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.jobs.isEmpty()) {

                Job job = null;
                try {
                    job = this.jobs.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(job != null) {
                    if(job.isPoisonous()) {
                        break;
                    }

                    if(job instanceof FileScanningJob) {
                        this.fileScanningJobs.add((FileScanningJob) job);
                    }

                    if(job instanceof WebScanningJob) {
                        this.webScanningJobs.add((WebScanningJob) job);
                    }
                }
            }
        }
        System.out.println("Job dispatcher shutting down");
    }

    @Override
    public void stop() {
        this.forever = false;
        this.jobs.add(new Job(true));
    }
}
