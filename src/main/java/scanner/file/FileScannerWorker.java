package scanner.file;

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

public class FileScannerWorker implements Callable<Map<String, Integer>> {

    private final List<File> files;
    private final List<String> keywords;

    public FileScannerWorker(List<File> files, List<String> keywords) {
        this.files = files;
        this.keywords = keywords;
    }

    @Override
    public Map<String, Integer> call() throws Exception {


        Map<String, Integer> occurrences = new HashMap<>();

        // For each file given in file scanning job
        for (File f : this.files) {
            try {
                // Read file
                List<String> content = Files.readAllLines(Paths.get(f.getAbsolutePath()));
                StringBuilder sb = new StringBuilder();
                content.forEach(line -> sb.append(line).append("\n"));
                String text = sb.toString();

                // For each keyword given by main
                for(String keyword : this.keywords) {
                    // Find number of occurrences
                    String keywordExtended = " " + keyword + " ";
                    Matcher matcher = Pattern.compile(keywordExtended).matcher(text);
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
                System.out.println("File: " + f.getPath() + ", cannot be read");
            }
        }
        return occurrences;
    }

}
