package scanner.web;

import jobs.ResultJob;
import jobs.ScanningJob;
import jobs.ScanningJobType;
import jobs.WebScanningJob;
import main.Stoppable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class WebScanner implements Runnable, Stoppable {

    private final BlockingQueue<ScanningJob> webScanningJobs;
    private final ConcurrentLinkedQueue<ResultJob> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final Map<String, Object> watchedUrls;
    private final List<String> keywords;

    private volatile boolean forever = true;

    public WebScanner(BlockingQueue<ScanningJob> webScanningJobs,
                      ConcurrentLinkedQueue<ResultJob> resultJobs,
                      ExecutorService threadPool,
                      ExecutorCompletionService<Map<String, Integer>> completionService,
                      Map<String, Object> watchedUrls,
                      List<String> keywords) {
        this.webScanningJobs = webScanningJobs;
        this.resultJobs = resultJobs;
        this.threadPool = threadPool;
        this.completionService = completionService;
        this.watchedUrls = watchedUrls;
        this.keywords = keywords;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.webScanningJobs.isEmpty()) {

                ScanningJob scanningJob = this.webScanningJobs.poll();

                System.out.println("Job: " + scanningJob.getPath() + ", hopCount: " + scanningJob.getHopCount());

                if(scanningJob.isPoisonous()) {
                    break;
                }
                String webUrl = scanningJob.getPath();
                int hopCount = scanningJob.getHopCount();

                if(this.watchedUrls.containsKey(webUrl)) {
                    continue;
                }

                this.watchedUrls.put(webUrl, new Object());

                findOtherUrls(webUrl, hopCount - 1);

                WebScanningJob webScanningJob = new WebScanningJob(webUrl);
                this.completionService.submit(new WebScannerWorker(webScanningJob, this.keywords));
                try {
                    List<Future<Map<String, Integer>>> occurrences = new ArrayList<>();
                    occurrences.add(this.completionService.take());
                    ResultJob resultJob = new ResultJob(ScanningJobType.WEB_SCANNING_JOB, webUrl, occurrences);
                    this.resultJobs.add(resultJob);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Web scanner shutting down");
    }

    private void findOtherUrls(String webUrl, int hopCount) {
        if(hopCount == 0) {
            return;
        }
        try {
            Document doc = Jsoup.connect(webUrl).get();
            Elements links = doc.select("a[href]");

            for(Element link : links) {
                String newLink = link.attr("abs:href");
                newLink = remoteQuery(newLink);
                if(!this.watchedUrls.containsKey(newLink)) {
                    this.webScanningJobs.add(new ScanningJob(newLink, hopCount));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String remoteQuery(String theURL) {
        int endPos;
        if (theURL.indexOf("?") > 0) {
            endPos = theURL.indexOf("?");
        } else if (theURL.indexOf("#") > 0) {
            endPos = theURL.indexOf("#");
        } else {
            endPos = theURL.length();
        }

        return theURL.substring(0, endPos);
    }

    @Override
    public void stop() {
        this.forever = false;
        this.threadPool.shutdown();
        this.webScanningJobs.add(new ScanningJob());
    }
}
