package scanner.web;

import jobs.WebScanningJob;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebScannerWorker implements Callable<Map<String, Integer>> {

    private final WebScanningJob webScanningJob;
    private final List<String> keywords;

    public WebScannerWorker(WebScanningJob webScanningJob, List<String> keywords) {
        this.webScanningJob = webScanningJob;
        this.keywords = keywords;
    }

    @Override
    public Map<String, Integer> call() throws Exception {

        Map<String, Integer> results = new HashMap<>();
        String webUrl = this.webScanningJob.getWebUrl();
        Document doc = Jsoup.connect(webUrl).get();

        for(String keyword : this.keywords) {
            String keywordExtended = " " + keyword + " ";
            Matcher matcher = Pattern.compile(keywordExtended).matcher(doc.outerHtml());
            int count = 0;
            while(matcher.find()) {
                count ++;
            }
            results.put(keyword, count);
        }
        return results;
    }

}
