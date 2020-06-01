package com.gmail.alexandrtalan;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.orc.TypeDescription;
import org.apache.orc.mapred.OrcStruct;
import org.apache.orc.mapreduce.OrcOutputFormat;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.util.Set;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.splitPreserveAllTokens;

public class TsvToOrcConverter {

    private static final String INPUT_FILE_DELIMITER = "\t";
    private static final String INPUT_HEADER = "id\tfirst_name\tlast_name\taccount_number\temail";
    private static final String OUTPUT_SCHEMA =
            "struct<id:int,first_name:string,last_name:string,account_number:int,email:string>";

    public static class OrcMapper extends Mapper<LongWritable, Text, NullWritable, OrcStruct> {

        private final Logger log = LoggerFactory.getLogger(OrcMapper.class);
        private final Validator validator = Validation.byDefaultProvider()
                .configure().messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()
                .getValidator();

        private final TypeDescription schema = TypeDescription.fromString(OUTPUT_SCHEMA);
        private final OrcStruct userStruct = (OrcStruct) OrcStruct.createValue(schema);
        private final NullWritable nothing = NullWritable.get();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            if (INPUT_HEADER.equals(value.toString())) {
                return; // skip header line
            }

            final String[] tokens = splitPreserveAllTokens(value.toString(), INPUT_FILE_DELIMITER);
            final User user = toUser(tokens);
            final boolean valid = validate(user);
            if (valid) { // skip invalid records
                prepareUserStruct(user);
                context.write(nothing, userStruct);
            }
        }

        private boolean validate(User user) {
            final Set<ConstraintViolation<User>> errors = validator.validate(user);
            errors.forEach(error ->
                    log.error("Record with id: {}. Error message: {}", user.getId(), error.getMessage())
            );

            return errors.isEmpty();
        }

        private User toUser(String[] tokens) {
            return new User(
                    tokens[0],
                    tokens[1],
                    tokens[2],
                    tokens[3],
                    tokens[4]
            );
        }

        private void prepareUserStruct(User user) {
            userStruct.setFieldValue("id", new IntWritable(parseInt(user.getId())));
            userStruct.setFieldValue("first_name", new Text(user.getFirstName()));
            userStruct.setFieldValue("last_name", new Text(user.getLastName()));
            userStruct.setFieldValue("account_number", new IntWritable(parseInt(user.getAccountNumber())));
            userStruct.setFieldValue("email", new Text(user.getEmail()));
        }
    }


    public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
        final JobArguments params = new JobArguments();
        JCommander.newBuilder().addObject(params).args(args).build();

        Configuration config = new Configuration();
        config.set("orc.mapred.output.schema", OUTPUT_SCHEMA);

        Job job = Job.getInstance(config, "TsvToOrcConverter");
        job.setJarByClass(TsvToOrcConverter.class);
        job.setMapperClass(OrcMapper.class);
        job.setOutputFormatClass(OrcOutputFormat.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(OrcStruct.class);
        job.setNumReduceTasks(0);

        FileInputFormat.addInputPath(job, new Path(params.getInputPath()));
        FileOutputFormat.setOutputPath(job, new Path(params.getOutputPath()));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }


    private static class User {

        @NotBlank(message = "Column id mustn't be empty.")
        private final String id;
        @NotBlank(message = "Column first_name mustn't be empty.")
        private final String firstName;
        @NotBlank(message = "Column last_name mustn't be empty.")
        private final String lastName;
        @Pattern(regexp = "\\d+", message = "Column account_number must be a number.")
        private final String accountNumber;
        @Email(message = "Column email must be valid.")
        private final String email;

        public User(String id, String firstName, String lastName, String accountNumber, String email) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.accountNumber = accountNumber;
            this.email = email;
        }

        public String getId() {
            return id;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getAccountNumber() {
            return accountNumber;
        }

        public String getEmail() {
            return email;
        }
    }


    private static class JobArguments {

        @Parameter(names = {"-i", "--input"})
        private String inputPath;
        @Parameter(names = {"-o", "--output"})
        private String outputPath;

        public JobArguments() {
        }

        public void setInputPath(String inputPath) {
            this.inputPath = inputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }

        public String getInputPath() {
            return inputPath;
        }

        public String getOutputPath() {
            return outputPath;
        }
    }
}
