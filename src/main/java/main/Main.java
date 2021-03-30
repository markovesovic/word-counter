package main;


import directory_crawler.CrawlerJob;
import directory_crawler.DirectoryCrawler;

import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Main {

    private static final String PROPERTIES_FILENAME = "src/main/resources/app.properties";
    private static final Properties properties = new Properties();

    private static final ConcurrentLinkedQueue<CrawlerJob> directoryNames = new ConcurrentLinkedQueue<>();

    private static DirectoryCrawler directoryCrawler;


    public static void main(String[] args) {
        loadProperties();
        initWorkers();
        forever();
    }

    private static void initWorkers() {
        String fileCorpusPrefix = (String) properties.get("file_corpus_prefix");
        int dirCrawlerSleepTime = Integer.parseInt(String.valueOf(properties.get("dir_crawler_sleep_time")));
        directoryCrawler = new DirectoryCrawler(directoryNames, fileCorpusPrefix, dirCrawlerSleepTime);
        Thread directoryCrawlerThread = new Thread(directoryCrawler, "directoryCrawler");
        directoryCrawlerThread.start();
    }

    private static void forever() {
        Scanner sc = new Scanner(System.in);

        while(true) {
            String line = sc.nextLine();

            if(line.equals("stop")) {
                System.out.println("Exiting. . .");
                directoryCrawler.stop();
                break;
            }

            if(line.equals("cfs")) {
                System.out.println("cfs");
                continue;
            }

            if(line.equals("cws")) {
                System.out.println("cws");
                continue;
            }

            if(!line.contains(" ")) {
                System.out.println("Niste uneli dobru komandu");
                continue;
            }

            if(line.split(" ").length > 2) {
                System.out.println("Uneli ste previse argumenata");
                continue;
            }


            String command = line.split(" ")[0];
            String param = line.split(" ")[1];

            if(command.equals("ad")) {
                String dirName = "src/main/resources/" + param;
                if(Files.notExists(Path.of(dirName))) {
                    System.out.println("Could not find dir: " + dirName);
                    continue;
                }
                CrawlerJob crawlerJob = new CrawlerJob(dirName);
                directoryNames.add(crawlerJob);
            }

            if(command.equals("aw")) {
                String websiteName = param;
                try {
                    new URL(websiteName).toURI();
                } catch (URISyntaxException | MalformedURLException e) {
                    System.out.println("Los format urla");
                    continue;
                }
                System.out.println("Dodajemo url");
            }

            if(command.equals("query")) {
                if(!param.startsWith("file|") || !param.startsWith("web|")) {
                    System.out.println("Los format argumenta");
                    break;
                }
                String corpusName = param.split("\\|")[1];
                if(param.startsWith("file|")) {
                    System.out.println("Trazimo rez za file");
                    break;
                }
                System.out.println("Trazimo rez za web");
            }

        }
        sc.close();
    }

    private static void loadProperties() {
        FileReader fr = null;
        try {
            fr = new FileReader(PROPERTIES_FILENAME);
            properties.load(fr);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(fr != null) {
                        fr.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
