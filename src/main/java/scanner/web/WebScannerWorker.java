package scanner.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebScannerWorker implements Callable<Map<String, Integer>> {

    private final String webUrl;
    private final List<String> keywords;

    public WebScannerWorker(String webUrl, List<String> keywords) {
        this.webUrl = webUrl;
        this.keywords = keywords;
    }

    @Override
    public Map<String, Integer> call() {

        Map<String, Integer> results = new HashMap<>();
        String webUrl = this.webUrl;
        Document doc = null;
        try {
            doc = Jsoup.connect(webUrl).get();
        } catch(Exception e) {
            System.out.println("Url: " + webUrl + " cannot be accessed");
            return null;
        }

        assert doc != null;
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
