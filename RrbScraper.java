///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.jsoup:jsoup:1.17.2
//DEPS com.opencsv:opencsv:5.9

import com.opencsv.CSVWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.util.*;

public class RrbScraper {

    static final String DOWNLOAD_FOLDER = "downloads";
    static final String CSV_FILE = "rrb_data.csv";

    public static void main(String[] args) {
        int startYear = 2003;
        int endYear = 2003;   // use full range after testing
        int startNumber = 1;
        int endNumber = 10;   // use full range after testing

        new File(DOWNLOAD_FOLDER).mkdirs();

        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(CSV_FILE))) {
            // Write header
            csvWriter.writeNext(new String[]{
                "Traktandentitel", "Sitzungsdatum", "Beschluss-Nr.",
                "Federführung", "Geschäftsart", "LocalFilePath", "SourceURL"
            });

            for (int year = startYear; year <= endYear; year++) {
                for (int number = startNumber; number <= endNumber; number++) {
                    String baseUrl = String.format("https://rrb.so.ch/beschlussnummer/%d_%d/", year, number);
                    try {
                        Document doc = Jsoup.connect(baseUrl)
                                .userAgent("Mozilla/5.0 (compatible; JBangScraper/1.0)")
                                .timeout(5000)
                                .ignoreHttpErrors(true)
                                .get();

                        if (doc.title().contains("Fehler") || doc.body().text().trim().isEmpty()) continue;

                        // Extract table key-value pairs
                        Map<String, String> metadata = extractMetadata(doc);

                        // Document links
                        Elements links = doc.select("table.tx-rrbpublications a[href^=/beschlussnummer/?tx_]");

                        for (Element link : links) {
                            String href = link.attr("href");
                            String absUrl = "https://rrb.so.ch" + href;
                            String label = link.text().replaceAll("[^a-zA-Z0-9-_]", "_");
                            String beschlussNr = metadata.getOrDefault("Beschluss-Nr.", year + "_" + number);
                            String fileName = beschlussNr.replace("/", "_") + "_" + label + ".pdf";
                            File file = new File(DOWNLOAD_FOLDER + File.separator + fileName);

                            downloadFile(absUrl, file);

                            // Write metadata + filepath + page URL to CSV
                            csvWriter.writeNext(new String[]{
                                    metadata.getOrDefault("Traktandentitel", ""),
                                    metadata.getOrDefault("Sitzungsdatum", ""),
                                    beschlussNr,
                                    metadata.getOrDefault("Federführung", ""),
                                    metadata.getOrDefault("Geschäftsart", ""),
                                    file.getPath(),
                                    baseUrl
                            });
                        }

                    } catch (IOException e) {
                        System.err.println("⚠️ Error accessing: " + baseUrl + " -> " + e.getMessage());
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {}
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Failed to write CSV: " + e.getMessage());
        }
    }

    private static Map<String, String> extractMetadata(Document doc) {
        Map<String, String> data = new HashMap<>();
        Elements rows = doc.select("table.tx-rrbpublications tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() == 2) {
                String key = cells.get(0).text().trim();
                String value = cells.get(1).text().trim();
                data.put(key, value);
            }
        }
        return data;
    }

    private static void downloadFile(String fileUrl, File file) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(fileUrl).openStream());
             FileOutputStream out = new FileOutputStream(file)) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                out.write(buffer, 0, len);
            }

        } catch (IOException e) {
            System.err.println("❌ Failed to download: " + fileUrl + " -> " + e.getMessage());
        }
        System.out.println("📥 Downloaded: " + file.getAbsolutePath());
    }
}
