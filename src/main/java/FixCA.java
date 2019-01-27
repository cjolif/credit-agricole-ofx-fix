import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.banking.AccountType;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.io.AggregateMarshaller;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.io.OFXParseException;
import com.webcohesion.ofx4j.io.v1.OFXV1Writer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class FixCA {
  public static void main(String[] args) throws IOException, OFXParseException, ParseException {
    Options options = new Options();
    options.addOption("f", "file", true, "specify OFX file to be fixed");
    options.addOption("o", "ouput", true, "specify OFX file to be written");
    CommandLineParser cliparser = new DefaultParser();
    CommandLine cmd = cliparser.parse(options, args);

    Path path = Paths.get(cmd.getOptionValue("f"));
    Path temp = Files.createTempFile("ofix-fix", ".ofx");
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(path), charset);
    content = content.replaceAll("<FITID>", "<FITID></FITID>");
    Files.write(temp, content.getBytes(charset));
    AggregateUnmarshaller<ResponseEnvelope> un = new AggregateUnmarshaller(ResponseEnvelope.class);
    try (Reader reader = Files.newBufferedReader(temp)) {
      ResponseEnvelope envelope = un.unmarshal(reader);
      BankingResponseMessageSet messageSet = (BankingResponseMessageSet) envelope.getMessageSet(MessageSetType.banking);
      for (BankStatementResponseTransaction responseMessage : messageSet.getStatementResponses()) {
        BankAccountDetails bankAccount = responseMessage.getMessage().getAccount();
        // new CA IDF OFX missing bank (mandatory) and branch (optional) IDs
        bankAccount.setBankId("18206");
        bankAccount.setBranchId("00061");
        // new CA IDX OFX mistyped ACCTTYPE into ACCTYPE add the correct version
        bankAccount.setAccountType(AccountType.CHECKING);
        List<Transaction> transactions = responseMessage.getMessage().getTransactionList().getTransactions();
        if (transactions != null) {
          transactions.forEach(transaction -> {
            // new CA IDX OFX missing FITID, building one from the hashes of the transaction parameters
            int hash = Objects.hash(transaction.getDatePosted(), transaction.getAmount(), transaction.getName());
            transaction.setId(Integer.toString(hash));
            if (transaction.getMemo() == null) {
              transaction.setMemo("AUTRE");
            }
          });
        }
      }
      AggregateMarshaller ma = new AggregateMarshaller();
      try (FileWriter fw = new FileWriter(cmd.getOptionValue("o"))) {
        OFXV1Writer ofxw = new OFXV1Writer(fw);
        try {
          ma.marshal(envelope, ofxw);
        } finally {
          ofxw.close();
        }
      }
    }
  }
}
