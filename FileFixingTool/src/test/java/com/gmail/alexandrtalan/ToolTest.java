package com.gmail.alexandrtalan;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ToolTest {

    private static final String DELIMITER = "\t";
    private static final Tool tool = new Tool();
    private static Path resourcesPath;
    private static Path tmpDirPath;

    @BeforeClass
    public static void init() throws IOException {
        resourcesPath = Paths.get("src/test/resources").toAbsolutePath();
        tmpDirPath = Files.createDirectory(resourcesPath.resolve("tmp"));
    }

    @AfterClass
    public static void clean() throws IOException {
        Files.walk(tmpDirPath)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    public void checkThatRowsAreCorrectlyWrittenToTheOutput() throws IOException {
        final Path output = tmpDirPath.resolve("output.tsv");
        final List<String> rowTokens = Arrays.asList("1", "A", "B", "C", "D");

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            tool.writeRowToOutput(rowTokens, writer);
        }

        try (BufferedReader reader = Files.newBufferedReader(output)) {
            final String expected = String.join(DELIMITER, rowTokens);
            final String actual = reader.readLine();

            assertEquals(actual, expected);
        }
    }

    @Test
    public void checkThatCorrectRowsAreNotFixed() {
        final String[] rowTokens = new String[]{"1", "A", "B", "C", "D"};
        final List<String> buffer = new ArrayList<>();
        tool.fixIncorrectRow(rowTokens, buffer);

        assertArrayEquals(rowTokens, buffer.toArray());
    }

    @Test
    public void checkCaseWhereEndLineAppearsAtTheEndOfTheColumn() {
        final String[] firstPart = new String[]{"1", "A", "B"};
        final String[] secondPart = new String[]{"", "C", "D"};

        final List<String> buffer = new ArrayList<>();
        tool.fixIncorrectRow(firstPart, buffer);
        tool.fixIncorrectRow(secondPart, buffer);

        final String[] expected = new String[]{"1", "A", "B", "C", "D"};

        assertArrayEquals(expected, buffer.toArray());
    }

    @Test
    public void checkCaseWhereEndLineAppearsAtTheStartOfTheColumn() {
        final String[] firstPart = new String[]{"1", "A", "B", ""};
        final String[] secondPart = new String[]{"C", "D"};

        final List<String> buffer = new ArrayList<>();
        tool.fixIncorrectRow(firstPart, buffer);
        tool.fixIncorrectRow(secondPart, buffer);

        final String[] expected = new String[]{"1", "A", "B", "C", "D"};

        assertArrayEquals(expected, buffer.toArray());
    }

    @Test
    public void checkCaseWhereEndLineAppearsAtTheMiddleOfTheColumn() {
        final String[] firstPart = new String[]{"1", "A", "B1"};
        final String[] secondPart = new String[]{"B2", "C", "D"};

        final List<String> buffer = new ArrayList<>();
        tool.fixIncorrectRow(firstPart, buffer);
        tool.fixIncorrectRow(secondPart, buffer);

        final String[] expected = new String[]{"1", "A", "B1\\nB2", "C", "D"};

        assertArrayEquals(expected, buffer.toArray());
    }

    @Test
    public void checkCaseWhereCombinationsOfListedAboveHappen() {
        final String[] firstPart = new String[]{"1", "A", ""};
        final String[] secondPart = new String[]{"B1"};
        final String[] thirdPart = new String[]{"B2", "C"};
        final String[] fourthPart = new String[]{"", "D"};

        final List<String> buffer = new ArrayList<>();
        tool.fixIncorrectRow(firstPart, buffer);
        tool.fixIncorrectRow(secondPart, buffer);
        tool.fixIncorrectRow(thirdPart, buffer);
        tool.fixIncorrectRow(fourthPart, buffer);

        final String[] expected = new String[]{"1", "A", "B1\\nB2", "C", "D"};

        assertArrayEquals(expected, buffer.toArray());
    }

    @Test
    public void checkTheToolUsingMostProbableInputData() throws IOException {
        final Path input = resourcesPath.resolve("input_data.tsv");
        final Path output = tmpDirPath.resolve("result.tsv");
        final String[] args = {"-i", input.toString(), "-o", output.toString()};

        Tool.main(args);

        try (BufferedReader reader = Files.newBufferedReader(output, UTF_8)) {
            final List<String> actual = reader.lines().collect(toList());
            final List<String> expected = Arrays.asList(
                    "1\tAlexnder\tTalanov\t907603\talexandrtalan@gmail.com",
                    "29\tAdena\tHobbs\\nBosley\t656184\tac.ipsum.Phasellus@ut.net",
                    "82\tJade\tBattle\t531695\tlectus.justo@lorem.co.uk",
                    "217\tBoris\\nHarrington\tHarrington\t325378\tneque.Nullam.ut@laoreetlectus.edu",
                    "337\tNEHRU\tMENDOZA\t859105\tporttitor.interdum.Sed@Loremipsum.co.uk",
                    "775\tBarbara\tHurley\t691210\tenim.Mauris.quis@magna.net",
                    "985\tCherokee\tIndian\t157172\tenim@disparturient.edu"
            );

            Assert.assertEquals(
                    String.join("\n", expected),
                    String.join("\n", actual)
            );
        }
    }

}
