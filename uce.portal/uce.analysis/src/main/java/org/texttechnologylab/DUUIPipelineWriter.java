package org.texttechnologylab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.uima.cas.SerialFormat;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.CasIOUtils;
import org.apache.uima.util.CasLoadMode;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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

    public static void main(String[] args) throws ParseException {
        // Parse command line arguments
        Options options = getOptions();
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption("help")) {
            System.out.println("Usage: java -jar DUUIPipelineWriter.jar [options]");
            System.out.println("Options:");
            System.out.println("  -inputFolder <path>   Path to the input folder containing XMI files");
            System.out.println("  -outputFolder <path>  Path to the output folder where processed files will be saved");
            System.out.println("  -configFile <path>    Path to the configuration file containing model URLs");
            System.out.println("  -help                 Display this help message");
            return;
        }
        // Validate and retrieve input and output folder paths
        String inputFolderPath = cmd.getOptionValue("inputFolder");
        String outputFolderPath = cmd.getOptionValue("outputFolder");
        String configFilePath = cmd.getOptionValue("configFile");
        if (inputFolderPath == null || outputFolderPath == null || configFilePath == null) {
            System.err.println("Missing required options. Use -help for usage information.");
            return;
        }
        File inputFolder = new File(inputFolderPath);
        File outputFolder = new File(outputFolderPath);
        File configFile = new File(configFilePath);
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            System.err.println("Input folder does not exist or is not a directory: " + inputFolder.getAbsolutePath());
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
        if (!configFile.exists() || !configFile.isFile()) {
            System.err.println("Configuration file does not exist or is not a file: " + configFile.getAbsolutePath());
            return;
        }
        DUUIPipeline pipeline = new DUUIPipeline();
        // Load URLs from the configuration file
        Map<String, String> urls = loadUrlsFromConfig(configFile);
        if (urls.isEmpty()) {
            System.err.println("No valid URLs found in the configuration file: " + configFile.getAbsolutePath());
            return;
        }
        // urls.put("Spacy", "http://spacy.service.component.duui.texttechnologylab.org");
        DUUIComposer composer;
        try {
            composer = pipeline.setListComposer(new HashMap<>(urls));
        } catch (Exception e) {
            log.error("Failed to initialize DUUIComposer", e);
            return;
        }
        log.info("Starting DUUI pipeline with input folder: {} and output folder: {}", inputFolder.getAbsolutePath(), outputFolder.getAbsolutePath());
        try (Stream<Path> paths = Files.walk(inputFolder.toPath())) {
            List<Path> inputFiles = paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".xmi")).map(path -> {
                try {
                    return Pair.with(path, Files.size(path));
                } catch (IOException e) {
                    return Pair.with(path, 0L);
                }
            }).sorted(Comparator.comparingLong(Pair::getValue1)).map(Pair::getValue0).toList();
            for (Path path : inputFiles) {
                if (!Files.isReadable(path)) {
                    log.warn("Skipping unreadable file: {}", path.toAbsolutePath());
                    continue;
                }
                Path outputPath = outputFolder.toPath().resolve(inputFolder.toPath().relativize(path));
                if (Files.exists(outputPath)) {
                    log.info("Output file already exists, skipping: {}", outputPath.toAbsolutePath());
                    continue;
                }
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
                JCas jCasOut;
                try {
                    jCasOut = pipeline.runPipeline(jCasIn, composer);
                } catch (Exception e) {
                    log.error("Error running DUUI pipeline on file: {}", path.toAbsolutePath(), e);
                    continue;
                }
                log.info("Pipeline execution completed for file: {}", path.toAbsolutePath());
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
     * Returns the command line options for the DUUIPipelineWriter.
     *
     * @return Options object containing command line options
     */
    @NotNull
    private static Options getOptions() {
        Options options = new Options();
        options.addOption("inputFolder", true, "Path to the input folder containing XMI files");
        options.addOption("outputFolder", true, "Path to the output folder where processed files will be saved");
        options.addOption("configFile", true, "Path to the configuration file containing model URLs");
        options.addOption("help", false, "Display this help message");
        return options;
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
                    boolean ignore = obj.has("ignore") && obj.get("ignore").getAsBoolean();
                    if (ignore) {
                        log.info("Ignoring model: {}", modelName);
                        continue;
                    }
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
