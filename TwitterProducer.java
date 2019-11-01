package com.github.simplepooja.kafka.tutorial2;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TwitterProducer {

//     String consumerKey = "qRAYItWMzhv9pLDIyTRdTfKRb";
//     String consumerSecret = "tOFMZOd3uu6GEHcbeUj25TwzZMcxwvUvsDrBxGgYJAuAokuTPY";
//     String token ="788387450372493312-mDnJvvgpwKKz8pJWhIKNIuqEQwIqiBJ";
//     String secret = "R8phLAQhPNIebl2Oq43CvlHBGmLx9LcfSgaQFeh1IHOz0";

    public TwitterProducer() {}

    public static void main(String[] args) {
        new TwitterProducer().run();

    }

    public void run() {
        /** Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(10000);
        //create twitter client
        Client client = createTwitterClient(msgQueue);
        client.connect();

        //create kafka producer
        KafkaProducer<String,String >producer = createKafkaProducer();

//        add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            log.info("Stopping the application");
            log.info("shutting down client from twitter");
            client.stop();
            log.info("closing producer");
            producer.close();
            log.info("done!");
        }));


        //loop to send tweets to kafka

        // on a different thread, or multiple different threads....
        while (!client.isDone()) {
            String msg = null;
            try {
                msg = msgQueue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                client.stop();
            }
            if (msg!=null) {
           log.info(msg);
           producer.send(new ProducerRecord<>("twitter_tweets", null, msg), new Callback() {
               @Override
               public void onCompletion(RecordMetadata metadata, Exception exception) {
                   if(exception != null) {
                       log.error("Something bad happened", exception);
                   }

               }
           });
            }
        }
        log.info("End of application");

    }

    public Client createTwitterClient(BlockingQueue<String>msgQueue){
        /** Declare the host you want to connect to, the endpoint, and authentication (basic auth or oauth) */
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
// Optional: set up some followings and track terms
        List<String> terms = Lists.newArrayList("kafka");
        hosebirdEndpoint.trackTerms(terms);

// These secrets should be read from a config file
        Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);

        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-Client-01")                              // optional: mainly for the logs
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));// optional: use this if you want to process client events

        Client hosebirdClient = builder.build();
// Attempts to establish a connection.
        return hosebirdClient;

    }

    public KafkaProducer<String,String> createKafkaProducer(){
        String bootStrapServers = "127.0.0.1:9092";
//        create Producer properties
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,bootStrapServers);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class.getName());

//        safe producer
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,"true");
        properties.setProperty(ProducerConfig.ACKS_CONFIG,"all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG,Integer.toString(Integer.MAX_VALUE));
        properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,"5");

//        create producer
        KafkaProducer<String,String> producer = new KafkaProducer<String, String>(properties);
        return producer;

    }
}
