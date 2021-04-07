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
    private final Map<String, Object> availableDomains;
    private final List<String> keywords;

    private volatile boolean forever = true;

    public WebScanner(BlockingQueue<WebScanningJob> webScanningJobs,
                      ConcurrentLinkedQueue<Job> resultJobs,
                      Map<String, Object> watchedUrls,
                      Map<String, Object> availableDomains,
                      List<String> keywords) {
        this.webScanningJobs = webScanningJobs;
        this.resultJobs = resultJobs;
        this.threadPool = Executors.newCachedThreadPool();
        this.completionService = new ExecutorCompletionService<>(this.threadPool);
        this.watchedUrls = watchedUrls;
        this.availableDomains = availableDomains;
        this.keywords = keywords;
    }

    @Override
    public void run() {
        while(this.forever) {

            while(!this.webScanningJobs.isEmpty()) {

                WebScanningJob job = this.webScanningJobs.poll();

                if(job.isPoisonous()) {
                    try {
                        boolean awaited = this.threadPool.awaitTermination(10, TimeUnit.SECONDS);
                        System.out.println(awaited);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                }

                String webUrl = job.getUrl();
                int hopCount = job.getHopCount();

                if(this.watchedUrls.containsKey(webUrl)) {
                    continue;
                }
                System.out.println("WebUrl: " + webUrl);

                this.watchedUrls.put(webUrl, new Object());

                findOtherUrls(webUrl, hopCount - 1);

                Future<Map<String, Integer>> occurrences = this.completionService.submit(new WebScannerWorker(webUrl, this.keywords));
                WebScanningResultJob resultJob = new WebScanningResultJob(occurrences, webUrl);
                this.resultJobs.add(resultJob);
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
                newLink = removeQuery(newLink);

                System.out.println("Link: " + newLink);
                try {
                    System.out.println("Domain: " + newLink.split("/")[2]);
                    link.attr("abs:domain");
                    this.availableDomains.put(newLink.split("/")[2], new Object());
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Index out of bounds");
                }

                if(!this.watchedUrls.containsKey(newLink)) {
                    this.webScanningJobs.add(new WebScanningJob(newLink, hopCount));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String removeQuery(String theURL) {
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
        this.webScanningJobs.add(new WebScanningJob());
    }
}
