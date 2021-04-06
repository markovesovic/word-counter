package scanner.web;

import jobs.Job;
import jobs.WebScanningJob;
import jobs.WebScanningResultJob;
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

    private final BlockingQueue<WebScanningJob> webScanningJobs;
    private final ConcurrentLinkedQueue<Job> resultJobs;
    private final ExecutorService threadPool;
    private final ExecutorCompletionService<Map<String, Integer>> completionService;
    private final Map<String, Object> watchedUrls;
    private final List<String> keywords;

    private volatile boolean forever = true;

    public WebScanner(BlockingQueue<WebScanningJob> webScanningJobs,
                      ConcurrentLinkedQueue<Job> resultJobs,
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

                WebScanningJob job = this.webScanningJobs.poll();

                if(job.isPoisonous()) {
                    break;
                }

                String webUrl = job.getUrl();
                int hopCount = job.getHopCount();

                if(this.watchedUrls.containsKey(webUrl)) {
                    continue;
                }

                this.watchedUrls.put(webUrl, new Object());

                findOtherUrls(webUrl, hopCount - 1);

                this.completionService.submit(new WebScannerWorker(webUrl, this.keywords));

                try {
                    Future<Map<String, Integer>> occurrences = this.completionService.take();
                    WebScanningResultJob resultJob = new WebScanningResultJob(occurrences, webUrl);
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
                    this.webScanningJobs.add(new WebScanningJob(newLink, hopCount));
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
        this.webScanningJobs.add(new WebScanningJob());
    }
}
