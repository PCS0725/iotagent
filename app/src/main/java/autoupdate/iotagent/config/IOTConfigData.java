package autoupdate.iotagent.config;

/**
 * This class holds some static data required for IOT operations. The data here is used at multiple
 * points and this class provides a single source of truth and a single point for modifying any value.
 * @author Prabhat Sharma
 */
public class IOTConfigData {

    //the unique thing name to which the device is registered
    public static final String DEVICE_ID = "\"430a5bc135a68f5af0544d3abbc0c092caff4d41e3cefce6574f2c688aee9344\"";

    //subscribe to this topic to get list of pending(queued and in progress jobs)
    public static final String GET_JOBS_SUBSCRIBE_TOPIC = String.format("$aws/things/%s/jobs/get/accepted", DEVICE_ID);

    //publish to this topic to get list of pending jobs
    public static final String GET_JOBS_PUBLISH_TOPIC = String.format("$aws/things/%s/jobs/get", DEVICE_ID);

    //publish to this topic to get job description of a particular job. Format the string with deviceId and jobId.
    public static final String GET_JOB_PUBLISH_TOPIC = "$aws/things/%s/jobs/%s/get";

    //subscribe to this topic to get job description of a job
    public static final String GET_JOB_SUBSCRIBE_TOPIC = String.format("$aws/things/%s/jobs/+/get/accepted", DEVICE_ID);

    //subscribe to this topic to get update messages about a job
    public static final String UPDATE_SUBSCRIBE_TOPIC = String.format("$aws/things/%s/jobs/+/update/#",DEVICE_ID);

    //publish to this topic to update jobStatus of a job. Format the string with deviceId and jobId.
    public static final String UPDATE_JOB_PUBLISH_TOPIC = "$aws/things/%s/jobs/%s/update";

    //codes for job statuses
    public static final int STATUS_SUCCEEDED = 0;
    public static final int STATUS_REJECTED = 1;
    public static final int STATUS_INPROGRESS = 2;
    public static final int STATUS_TIMED_OUT = 3;
    public static final int STATUS_QUEUED = 4;
    public static final int STATUS_FAILED = 5;

}
