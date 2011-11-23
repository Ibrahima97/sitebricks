package com.google.sitebricks.mail;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Guice;
import com.google.sitebricks.mail.Mail.Auth;
import com.google.sitebricks.mail.MailClient.WireError;
import com.google.sitebricks.mail.imap.Flag;
import com.google.sitebricks.mail.imap.Folder;
import com.google.sitebricks.mail.imap.FolderStatus;
import com.google.sitebricks.mail.imap.Message;
import com.google.sitebricks.mail.imap.MessageStatus;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class MailClientIntegrationTest {

  static {
    java.util.logging.ConsoleHandler fh = new java.util.logging.ConsoleHandler();
    java.util.logging.Logger.getLogger("").addHandler(fh);
    java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINEST);
  }

  private static final FolderObserver PRINTING_OBSERVER = new FolderObserver() {
    @Override
    public void changed(Set<Integer> added, Set<Integer> removed) {
      System.out.println("New mail arrived!!");
    }
  };

  public static void main(String... args) throws InterruptedException, ExecutionException {
    Mail mail = Guice.createInjector().getInstance(Mail.class);
    final MailClient client = mail.clientOf("imap.gmail.com", 993)
         .prepare(Auth.SSL, System.getProperty("sitebricks-mail.username"),
             System.getProperty("sitebricks-mail.password"));

    try {
      client.connect();
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>");
      WireError lastError = client.lastError();
      System.out.println(lastError.expected());
      System.out.println(lastError.message());
      System.out.println(lastError.trace());
      System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }

    List<String> capabilities = client.capabilities();
    System.out.println("CAPS: " + capabilities);

    System.out.println("FOLDERS: " + client.listFolders().get());
    try {
      Folder f = client.open("Thumping through the brush.", false).get();
      System.out.println("Expected failure attempting to open invalid folder.");
    } catch (ExecutionException ee) {
      // expected.
    }

    final ListenableFuture<FolderStatus> fStatus =
        client.statusOf("[Gmail]/All Mail");
    ListenableFuture<Folder> future = client.open("[Gmail]/All Mail", true);
    final Folder allMail = future.get();
    final FolderStatus folderStatus = fStatus.get();
    System.out
        .println("Folder opened: " + allMail.getName() + " with count " + folderStatus.getMessages());

    future.addListener(new Runnable() {
      @Override
      public void run() {
        ListenableFuture<List<MessageStatus>> messageStatuses =
            client.list(allMail, folderStatus.getMessages() - 1, -1);

        ListenableFuture<List<Message>> messages = client.fetch(allMail,
            folderStatus.getMessages() - 1, -1);
        try {
          for (MessageStatus messageStatus : messageStatuses.get()) {
            System.out.println(messageStatus);
          }

          for (Message message : messages.get()) {
//            System.out.println(ToStringBuilder.reflectionToString(message));
            for (Message.BodyPart bodyPart : message.getBodyParts()) {
//              System.out.println(ToStringBuilder.reflectionToString(bodyPart));
            }

            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>");
            System.out.println(message.getImapUid());
            System.out.println(message.getHeaders().get("Message-ID"));
            System.out.println(message.getHeaders());
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>\n\n\n");
          }

          System.out.println("Gmail flags set: " +
              client.addFlags(allMail, messages.get().get(0).getImapUid
                  (), ImmutableSet.of(Flag.SEEN)).get());

          System.out.println("Fetched: " + messages.get().size());
        } catch (InterruptedException e) {
          e.printStackTrace();
        } catch (ExecutionException e) {
          e.printStackTrace();
        }
        client.disconnect();

        System.exit(0);
      }
    }, Executors.newCachedThreadPool());


  }
}
