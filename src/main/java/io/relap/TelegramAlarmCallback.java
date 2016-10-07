package io.relap;

import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackConfigurationException;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackException;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.NumberField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.streams.Stream;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

public class TelegramAlarmCallback implements AlarmCallback {

    private Configuration configuration;
    private FileWriter log = null;

    @Override
    public void initialize(Configuration configuration) throws AlarmCallbackConfigurationException {
        this.configuration = new Configuration(configuration.getSource());
        String logFilepath = configuration.getString("filelog");
        if (logFilepath != null && logFilepath.length() > 0) {
            try {
                log = new FileWriter(logFilepath, true);
                log.write("Logging started\n");
                log.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void call(Stream stream, AlertCondition.CheckResult checkResult) throws AlarmCallbackException {
        try {
            String host = configuration.getString("host");
            int port = configuration.getInt("port");
            checkResult.getMatchingMessages().size();

            String message;
            try {
                message = createRequestMsg(stream, checkResult) + "\n";
            } catch (Exception ex) {
                message = "Error: " + ex.getMessage() + "\n" + getFullStackTrace(ex) + "\n";
            }

            if (log != null) {
                log.write(message);
                log.write('\n');
                log.flush();
            }

            Socket socket = new Socket();
            try {
                InetSocketAddress address = new InetSocketAddress(host, port);
                socket.connect(address, 1000);
                socket.setSoTimeout(1000);

                String request = createRequestBody(message);
                OutputStream os = socket.getOutputStream();
                os.write(request.getBytes());
                os.flush();

                InputStream is = socket.getInputStream();
                StringBuilder sb = new StringBuilder();
                int ch;
                try {
                    while ((ch = is.read()) != -1)
                        sb.append((char) (ch));
                } catch (SocketTimeoutException e) {
                    // expected behavior because server does not close connection
                }
                is.close();
                os.close();
                String response = sb.toString();
                String[] responseLines = response.toLowerCase().split("\n");
                if (!(responseLines.length == 2
                        && responseLines[0].matches(".*answer \\d+.*")
                        && responseLines[1].contains("success"))) {
                    if (log == null) {
                        System.out.println("Wrong answer:\n" + response + "\n");
                    } else {
                        log.write("Wrong answer:\n" + response);
                        log.write("\n");
                        log.flush();
                    }
                }
            } finally {
                socket.close();
            }
        } catch (IOException ex) {
            try {
                if (log != null) {
                    log.write(getFullStackTrace(ex));
                    log.flush();
                }
            } catch (IOException e) {
            }
            ex.printStackTrace();
        }
    }

    private String createRequestBody(String msg) {
        StringBuilder sb = new StringBuilder();
        String[] nicks = configuration.getString("nicks").split("(\\s+|,)");

        boolean first = true;
        for (String nick : nicks) {
            if (!first)
                sb.append('\n');
            first = false;
            sb.append("msg ");
            sb.append(nick.trim());
            sb.append(' ');
            sb.append(msg);
        }

        return sb.toString();
    }


    private String createRequestMsg(Stream stream, AlertCondition.CheckResult checkResult) {
        String description = checkResult.getResultDescription();
        String title = stream.getTitle();
        if (title != null)
            return description.replace("Stream", title);
        else
            return description;
    }

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
        final ConfigurationRequest configurationRequest = new ConfigurationRequest();
        configurationRequest.addField(new TextField(
                "nicks", "Nicks", "Bob, Alice", "Nicks of notified developers (Firstname_Lastname Firstname2_Lastname2)",
                ConfigurationField.Optional.NOT_OPTIONAL));
        configurationRequest.addField(new TextField("host", "Host", "localhost",
                "Host of tg-cli daemon", ConfigurationField.Optional.NOT_OPTIONAL));
        configurationRequest.addField(new NumberField("port", "Port", 80,
                "Post of tg-cli daemon", ConfigurationField.Optional.NOT_OPTIONAL));
        configurationRequest.addField(new TextField("filelog", "File log", "/tmp/telegramalarmcallback.log",
                "File path for debug logging"));
        return configurationRequest;
    }

    @Override
    public String getName() {
        return "TelegramAlarmCallback";
    }

    @Override
    public Map<String, Object> getAttributes() {
        return configuration.getSource();
    }

    @Override
    public void checkConfiguration() throws ConfigurationException {
        if (!configuration.stringIsSet("nicks")) {
            throw new ConfigurationException("Assign nicks");
        }

        if (!configuration.stringIsSet("host")) {
            throw new ConfigurationException("Assign host");
        }

        if (!configuration.intIsSet("port")) {
            throw new ConfigurationException("Assign port");
        }
    }

    private String getFullStackTrace(Throwable ex) {
        StringWriter errors = new StringWriter();
        ex.printStackTrace(new PrintWriter(errors));
        return errors.toString();
    }

}
