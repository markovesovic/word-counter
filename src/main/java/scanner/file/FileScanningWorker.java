package scanner.file;

import job_dispatcher.FileScanningJob;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileScanningWorker implements Callable<Map<String, Integer>> {

    private final FileScanningJob fileScanningJob;
    private final List<String> keywords;

    public FileScanningWorker(FileScanningJob fileScanningJob, List<String> keywords) {
        this.fileScanningJob = fileScanningJob;
        this.keywords = keywords;
    }

    @Override
    public Map<String, Integer> call() throws Exception {

        Map<String, Integer> occurrences = new HashMap<>();

        // For each file given in file scanning job
        for (File f : this.fileScanningJob.getFiles()) {
            try {
                // Read file
                List<String> content = Files.readAllLines(Paths.get(f.getAbsolutePath()));
                StringBuilder sb = new StringBuilder();
                content.forEach(line -> sb.append(line).append("\n"));
                String text = sb.toString();

                // For each keyword given by main
                for(String keyword : this.keywords) {
                    // Find number of occurrences
                    keyword = " " + keyword + " ";
                    Matcher matcher = Pattern.compile(keyword).matcher(text);
                    int count = 0;
                    while(matcher.find()) {
                        count++;
                    }
                    // If entry already exist then update it
                    if(occurrences.containsKey(keyword)) {
                        occurrences.put(keyword, occurrences.get(keyword) + count);
                        continue;
                    }
                    // If entry does not exist then insert it
                    occurrences.put(keyword, count);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return occurrences;
    }

}
