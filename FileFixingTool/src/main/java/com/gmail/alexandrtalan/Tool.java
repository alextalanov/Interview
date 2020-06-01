package com.gmail.alexandrtalan;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.splitByWholeSeparatorPreserveAllTokens;

public class Tool {

    private static final Logger LOG = LoggerFactory.getLogger(Tool.class);
    private static final int ROW_SIZE = 5;
    private static final String DELIMITER = "\t";

    @Parameter(names = {"--input", "-i"}, required = true)
    private String inputFile;

    @Parameter(names = {"--output", "-o"}, required = true)
    private String outputFile;

    public static void main(String[] args) throws IOException {
        final Tool tool = new Tool();
        JCommander.newBuilder()
                .addObject(tool)
                .build()
                .parse(args);

        tool.run();
    }

    private void run() throws IOException {
        final Path inputPath = Paths.get(inputFile);
        final Path outputPath = Paths.get(outputFile);

        LOG.info("Input file: {}", inputPath);
        LOG.info("Output file: {}", outputPath);

        try (
                BufferedReader reader = prepareBOMSkipBufferedReader(inputPath);
                BufferedWriter writer = Files.newBufferedWriter(outputPath, UTF_8, CREATE_NEW)
        ) {
            final List<String> correctionBuffer = new ArrayList<>(ROW_SIZE);
            reader.lines()
                    .map(line -> splitByWholeSeparatorPreserveAllTokens(line, DELIMITER))
                    .map(StringUtils::stripAll)
                    .forEach(rowTokens -> {
                        if (rowTokens.length != ROW_SIZE) {
                            fixIncorrectRow(rowTokens, correctionBuffer);

                            if (correctionBuffer.size() == ROW_SIZE) {
                                writeRowToOutput(correctionBuffer, writer);
                                correctionBuffer.clear();
                            }
                        } else {
                            writeRowToOutput(asList(rowTokens), writer);
                        }
                    });
        }
    }

    protected void fixIncorrectRow(String[] tokens, List<String> correctionBuffer) {
        final String head = head(tokens);

        if (correctionBuffer.isEmpty()) {
            correctionBuffer.addAll(asList(tokens));
        } else if (isBlank(head)) {
            correctionBuffer.addAll(asList(tail(tokens))); //the case where \n appears at the end of the token
        } else {
            final int lastIndex = correctionBuffer.size() - 1;
            final String lastBuffered = correctionBuffer.get(lastIndex);

            if (isBlank(lastBuffered)) {
                correctionBuffer.set(lastIndex, head); //the case where \n appears at the start of the token

            } else {                                  //the case where \n appears as a separator of the token
                final String fixedField = lastBuffered + "\\n" + head;
                correctionBuffer.set(lastIndex, fixedField);
            }
            correctionBuffer.addAll(asList(tail(tokens)));
        }
    }

    protected void writeRowToOutput(List<String> tokens, BufferedWriter writer) {
        final String line = String.join(DELIMITER, tokens);
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException ex) {
            LOG.error("Issue when tried to write results.", ex);
            throw new IllegalStateException(ex);
        }
    }

    private BufferedReader prepareBOMSkipBufferedReader(Path inputPath) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new BOMInputStream(
                                newInputStream(inputPath), ByteOrderMark.UTF_16LE), UTF_16LE));
    }

    private <T> T head(T[] array) {
        if (isEmpty(array)) {
            throw new IllegalStateException("Array mustn't be empty or null.");
        }
        return array[0];
    }

    private <T> T[] tail(T[] array) {
        if (isEmpty(array)) {
            throw new IllegalStateException("Array mustn't be empty or null.");
        }
        return Arrays.copyOfRange(array, 1, array.length);
    }
}
