package autoupdate.iotagent.util;


import android.content.Context;
import android.util.Log;

import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Timer;
import java.util.TimerTask;

import autoupdate.iotagent.config.IOTConfigData;

/**
 * This class handles communication with AWS IOT backend.
 * It is responsible for fetching keystore and connecting to the mqtt broker.
 * It also handles publishing and subscribing to IOT topics.
 * We only need a single instance of this class to connect to the mqtt broker, hence it is Singleton
 * @author Prabhat Sharma
 */
public class IOTHelper {

    private Context context;

    static final String LOG_TAG = IOTHelper.class.getCanonicalName();

    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a2o2raq026ld6u-ats.iot.us-east-1.amazonaws.com";

    private static final String AWS_IOT_POLICY_NAME = "pubsubiotpolicy";

    private static final Regions MY_REGION = Regions.US_EAST_1;

    private static final String KEYSTORE_NAME = "iot_keystore";

    private static final String KEYSTORE_PASSWORD = "password";

    private final String CERT_FILE_NAME = "deviceCert.pem";

    private final String PRIVKEY_FILE_NAME = "deviceCert.key";

    private static final String IOT_GENERATED_CERT = "default";

    private static final String CERTIFICATE_ID  = "b9eadbe12eb863304c7d72e210065a3565e909b9acdf10901f11910e15d8ef45";

    private static final String TEMP_CERT_ID = "6a96fc1677619ae4a5640364a830ff8349de6c63a85d6738a98546c20730ea81";

    AWSIotClient mIotAndroidClient;
    AWSIotMqttManager mqttManager;
    String clientId = IOTConfigData.DEVICE_ID;
    String keystorePath;
    String keystoreName;
    String keystorePassword;
    KeyStore clientKeyStore = null;
    String certificateId;
    JobMessageHandler jobMessageHandler;

    private static IOTHelper iotHelper = null;

    /**
     * Constructor is private to prevent other classes from creating multiple instances of IOTHelper
     * class.
     * @param ctx context of execution
     */
    private IOTHelper(Context ctx) {
        this.context = ctx;
    }

    /**
     * This method ensures there is only one instance of the IOThelper class.
     * @param context context of execution
     * @return instance of IOTHelper
     */
    public static IOTHelper getInstance(Context context)
    {
        if(iotHelper == null)
            iotHelper = new IOTHelper(context);
        return iotHelper;
    }

    /**
     * This method gets AWS credentials and then initiates a connection process.
     * @param keystorePath path to keystore on client device
     */
    public void init(final String keystorePath) throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {

        jobMessageHandler = new JobMessageHandler(context);

        initIoTClient(keystorePath);
        connectClick();

    }

    /**
     * This method initiates a connection with mqtt service. Once connected, it calls subscribeClick
     * method to subscribe the device to some topics.
     */
    public void connectClick() {
        Log.d(LOG_TAG, "clientId = " + clientId);

        try {
            mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(LOG_TAG, "Status = " + String.valueOf(status));
                    if (throwable != null) {
                        Log.e(LOG_TAG, "Connection error.", throwable);
                    }
                    if (status.equals(AWSIotMqttClientStatus.Connected)) {

                        subscribeClick(IOTConfigData.GET_JOBS_SUBSCRIBE_TOPIC);
                        subscribeClick(IOTConfigData.GET_JOB_SUBSCRIBE_TOPIC);
                        Log.d(LOG_TAG, "Subscribing to topic : \n" + IOTConfigData.GET_JOBS_SUBSCRIBE_TOPIC );
                        Log.d(LOG_TAG, "Subscribing to topic : \n" + IOTConfigData.GET_JOB_SUBSCRIBE_TOPIC);
                        getPendingJobsPoller();
                    }
                }
            });
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Connection error.", e);
        }
    }

    /**
     * This method subscribes the device to given topic and listens to those topic. When a message is
     * received, onMessageReceived callback is called and the message is passed to appropriate handlers.
     * @param topic the topic name to subscribe to.
     */
    public void subscribeClick(String topic) {

        try {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0,
                    new AWSIotMqttNewMessageCallback() {
                        @Override
                        public void onMessageArrived(final String topic, final byte[] data) {

                            String message = new String(data);
                            Log.d(LOG_TAG, "Message arrived");
                            Log.d(LOG_TAG, " Topic: " + topic + " Message: " + message);

                            if(topic.equals(IOTConfigData.GET_JOBS_SUBSCRIBE_TOPIC)) {
                                    jobMessageHandler.handleGetJobsResponse(message);
                            }
                            else {
                                    jobMessageHandler.handleJobMessage(message);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Subscription error.", e);
        }
    }

    /**
     * This method is called once connection with mqtt is established. It publishes to a topic at
     * regular intervals to receive list of pending jobs
     */
    public void getPendingJobsPoller(){
        Log.i(LOG_TAG, "starting polling for new jobs");
        try {
            Timer timer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    String clientToken = "{\"clientToken\":\"123\"}";
                    publish(clientToken, IOTConfigData.GET_JOBS_PUBLISH_TOPIC);
                }
            };

            timer.schedule(timerTask, 0, 30000);
        }catch (Exception e){
            Log.e(LOG_TAG, "error in polling : " + e.getMessage());
        }
    }

    /**
     * This method publishes to a topic to get job details of a particular job
     * @param jobId the job identifier
     */
    public void publishForJobDescription(String jobId){
        String topic = String.format(IOTConfigData.GET_JOB_PUBLISH_TOPIC, IOTConfigData.DEVICE_ID, jobId);
        String clientToken = "{\"clientToken\":\"123\"}";
        publish(clientToken, topic);
    }


    /**
     * Handles the communication with mqtt when a message is published
     * @param msg the message string
     * @param topic topic to publish message to
     */
    public void publish(String msg, String topic) {
        try {
            mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Publish error.", e);
        }
    }

    /**
     * Disconnects the device from IOT backend
     */
    public void disconnect() {
        try {
            mqttManager.disconnect();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Disconnect error.", e);
        }
    }

    /**
     * Converts an inputstream to a string
     * @param is the input stream to convert
     * @return string generated from inputstream
     * @throws IOException reading from inputstream may throw IOException
     */
    public static String convertStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    /**
     * Reads a file into a string
     * @param filePath the path of file to read
     * @return the generated string from file
     * @throws IOException if filenotfound or exception while reading inputstream
     */
    public static String getStringFromFile (String filePath) throws IOException {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        fin.close();
        return ret;
    }

    /**
     * Reads a certificate(.pem) file into a string
     * @return the certificate string
     * @throws IOException
     */
    private String getCertificateString() throws IOException {
        //Move the certificate path to configdata, avoid hardcoded value.
        String path = context.getExternalFilesDir("Download") + File.separator + CERT_FILE_NAME;
        String certString = getStringFromFile(path);
        Log.i(LOG_TAG, "Cert  : " + certString);
        return  certString;
    }

    /**
     * Reads a private key file(.key) to a string
     * @return the private key string
     * @throws IOException
     */
    private String getPrivateKeyString() throws IOException {

        //Move the file path to configdata, avoid hardcoded value.
        String path = context.getExternalFilesDir("Download") + File.separator + PRIVKEY_FILE_NAME;
        String privateKeyString = getStringFromFile(path);
        Log.i(LOG_TAG, "Private key : " + privateKeyString);
        return  privateKeyString;

    }

    /**
     * Initializes clientKeyStore and mqttManager
     * @param keystorePath
     * @throws InvalidKeySpecException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    void initIoTClient(String keystorePath) throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {

        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);

        mqttManager.setKeepAlive(10);
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);

        keystoreName = KEYSTORE_NAME;
        keystorePassword = KEYSTORE_PASSWORD;
        certificateId = CERTIFICATE_ID; //set here which cert you want to use.

        //Check if certificate already exists in the keystore
        try {
            clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                    keystorePath, keystoreName, keystorePassword);
        }catch (Exception e){
            Log.e(LOG_TAG, "could not fetch keystore " + e.getMessage());
        }

        //save the certificate with certId in the keystore, if not yet available.
        if(clientKeyStore == null) {
            try {
                String privateKeyString = getPrivateKeyString();
                String certString = getCertificateString();

                AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId, certString, privateKeyString,
                        keystorePath, keystoreName, keystorePassword);

                clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId, keystorePath, keystoreName, keystorePassword);

            } catch (Exception e) {
                Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e);
            }
        }
    }
}


