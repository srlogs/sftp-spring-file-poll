package org.example.ScratchMvn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.google.gson.Gson;
import com.jcraft.jsch.ChannelSftp.LsEntry;

import org.example.ScratchMvn.model.QueueResponse;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.filters.SftpSimplePatternFileListFilter;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

@SpringBootApplication
public class App {

    private ServiceBusSenderClient senderClient = null;

    public static void main(String[] args) {
        new SpringApplicationBuilder(App.class).run(args);
    }

    /**
     * Creates connection
     * 
     * @return
     */
    @Bean
    public SessionFactory<LsEntry> sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost("sftp.aspiresys.com");
        factory.setPort(22);
        factory.setUser("SingPost");
        factory.setPassword("S!ng@P0$t");
        factory.setAllowUnknownKeys(true);
        return new CachingSessionFactory<LsEntry>(factory);
    }

    @Bean
    public SftpInboundFileSynchronizer sftpInboundFileSynchronizer() {
        SftpInboundFileSynchronizer fileSynchronizer = new SftpInboundFileSynchronizer(sftpSessionFactory());
        fileSynchronizer.setDeleteRemoteFiles(false);
        fileSynchronizer.setRemoteDirectory("upload/20211110");
        fileSynchronizer.setFilter(new SftpSimplePatternFileListFilter("*.txt"));
        return fileSynchronizer;
    }

    @Bean
    @InboundChannelAdapter(channel = "sftpChannel", poller = @Poller(fixedDelay = "1000"))
    public MessageSource<File> sftpMessageSource() {
        SftpInboundFileSynchronizingMessageSource source = new SftpInboundFileSynchronizingMessageSource(
                sftpInboundFileSynchronizer());
        source.setLocalDirectory(new File("C:\\home\\SFTPFiles\\Detail"));
        source.setAutoCreateLocalDirectory(true);
        source.setLocalFilter(new AcceptOnceFileListFilter<File>());

        return source;
    }

    @Bean
    @ServiceActivator(inputChannel = "sftpChannel")
    public MessageHandler handler() throws IOException {
        return new MessageHandler() {

            @Override
            public void handleMessage(Message<?> message) throws MessagingException {

                QueueResponse response = new QueueResponse();

                try {
                    String content = Files.readString(Paths.get(((File) message.getPayload()).getAbsolutePath()));

                    response.setFile_data(content);
                    response.setFile_name(((File) message.getPayload()).getName());
                    sendMessage(response);
                    // TODO: blob storage upload

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        };
    }

    private ServiceBusSenderClient getServiceBusSenderClient() {
        String connectionString = "Endpoint=sb://sp-dev-service-bus.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=6vPjOdKNdba52YU+WCThgaf3OzxyEcIj4kBTZwPrw5k=";
        String queueName = "sp-importjobs-processtntrecord-queue";

        if (senderClient == null) {
            this.senderClient = new ServiceBusClientBuilder()
                    .connectionString(connectionString)
                    .sender()
                    .queueName(queueName)
                    .buildClient();
        }

        return this.senderClient;
    }

    private void sendMessage(QueueResponse response) {

        // send one message to the queue
        this.getServiceBusSenderClient()
                .sendMessage(new ServiceBusMessage(new Gson().toJson(response)).setContentType("application/json"));
        System.out.println("Sent a single message to the queue: ");
    }
}
