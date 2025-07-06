package org.texttechnologylab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.uima.cas.SerialFormat;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.CasIOUtils;
import org.apache.uima.util.CasLoadMode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * DUUIPipelineWriter is a utility class that processes XMI files in a specified input folder using the DUUI pipeline.
 * It reads each XMI file, runs the DUUI pipeline, and writes the processed output to a specified output folder.
 * The class uses DUUIComposer to configure the pipeline with various models.
 * This class is only intended for testing purposes and should not be used in production.
 * <p>
 * This needs to be run with vm options:
 * <code>--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.util/java.base=ALL-UNNAMED</code>
 */
public class DUUIPipelineWriter {

    private static final Logger log = LoggerFactory.getLogger(DUUIPipelineWriter.class);

    public static void main(String[] args) {
        File inputFolder = new File("C:\\Users\\quent\\Downloads\\Compressed\\UCE-Data\\input\\Reichstag\\1. Leg.-Periode\\1871,1");
        File outputFolder = new File("C:\\Users\\quent\\Downloads\\Compressed\\UCE-Data\\input\\Reichstag\\1. Leg.-Periode\\out");
        DUUIPipeline pipeline = new DUUIPipeline();
        Map<String, String> urls = new HashMap<>();
        urls.put("Spacy", "http://spacy.service.component.duui.texttechnologylab.org");
        DUUIComposer composer;
        try {
            composer = pipeline.setListComposer(new HashMap<>(urls));
        } catch (Exception e) {
            log.error("Failed to initialize DUUIComposer", e);
            return;
        }
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            log.error("Input folder does not exist or is not a directory: {}", inputFolder.getAbsolutePath());
            return;
        }
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                System.err.println("Failed to create output folder: " + outputFolder.getAbsolutePath());
                return;
            }
        } else if (!outputFolder.isDirectory()) {
            System.err.println("Output path exists but is not a directory: " + outputFolder.getAbsolutePath());
            return;
        }
        log.info("Starting DUUI pipeline with input folder: {} and output folder: {}", inputFolder.getAbsolutePath(), outputFolder.getAbsolutePath());
        try (Stream<Path> paths = Files.walk(inputFolder.toPath())) {
            List<Path> inputFiles = paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".xmi")).toList();
            for (Path path : inputFiles) {
                log.info("Processing file: {}", path.toAbsolutePath());
                JCas jCasIn = JCasFactory.createJCas();
                log.info("Loading file into JCas: {}", path.toAbsolutePath());
                try (FileInputStream fis = new FileInputStream(path.toFile())) {
                    CasIOUtils.load(fis, null, jCasIn.getCas(), CasLoadMode.LENIENT);
                } catch (Exception e) {
                    log.error("Error processing file: {}", path.toAbsolutePath(), e);
                    continue;
                }
                log.info("File loaded successfully, running DUUI pipeline...");
                JCas jCasOut = pipeline.runPipeline(jCasIn, composer);
                log.info("Pipeline execution completed for file: {}", path.toAbsolutePath());
                Path outputPath = outputFolder.toPath().resolve(inputFolder.toPath().relativize(path));
                Files.createDirectories(outputPath.getParent());
                log.info("Saving processed file to: {}", outputPath.toAbsolutePath());
                try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                    CasIOUtils.save(jCasOut.getCas(), fos, SerialFormat.XMI);
                    log.info("Saved processed file to: {}", outputPath.toAbsolutePath());
                } catch (Exception e) {
                    log.error("Error saving processed file: {}", outputPath.toAbsolutePath(), e);
                }
            }
            log.info("DUUI pipeline execution completed successfully.");
        } catch (Exception e) {
            log.error("An error occurred during the DUUI pipeline execution", e);
        }
    }

    /**
     * Loads URLs from a configuration file.
     *
     * @param configFile the configuration file containing model URLs
     * @return a map of model names to their corresponding URLs
     */

    @NotNull
    private static Map<String, String> loadUrlsFromConfig(File configFile) {
        Map<String, String> urls = new HashMap<>();
        try {
            JsonArray array = JsonParser.parseString(Files.readString(configFile.toPath())).getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();
                    String modelName = obj.get("name").getAsString();
                    String url = obj.get("url").getAsString();
                    urls.put(modelName, url);
                } else {
                    log.warn("Invalid JSON object in config file: {}", element);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load URLs from config file: {}", configFile.getAbsolutePath(), e);
        }
        return urls;
    }

}
