package autoupdate.iotagent.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;

import autoupdate.iotagent.config.IOTConfigData;

/**
 * This class processes the jobs received form IOTHelper class and updates their status.
 * It is also responsible for downloading the update and sending the broadcast to target apps.
 * @author Prabhat Sharma
 */

public class JobMessageHandler {

    private final String LOG_TAG = "JobMessageHandler";

    private PackageManager packageManager;

    private IOTHelper iotHelper;

    private  Context context;

    public JobMessageHandler(Context context){
        this.context = context;
    }

    /**
     * This method receives the list of pending jobs as a string. It combines queued and in_progress
     * jobs and publishes to a topic to receive respective job details.
     * @param message the string containing list of queued and in_progress jobs.
     */
    public void handleGetJobsResponse(String message){
        iotHelper = IOTHelper.getInstance(context);
        try {
            JSONObject response = new JSONObject(message);
            JSONArray queuedJobs = (JSONArray)response.get("queuedJobs");
            JSONArray inProgressJobs = (JSONArray)response.get("inProgressJobs");
            JSONArray jobs = inProgressJobs;
            for(int i = 0; i<queuedJobs.length(); ++i){
                jobs.put(queuedJobs.get(i));
            }

            for(int i = 0; i<inProgressJobs.length(); ++i){
                JSONObject job = jobs.getJSONObject(i);
                String jobId = job.getString("jobId");
                iotHelper.publishForJobDescription(jobId);
            }
        }catch (Exception je){
            Log.e(LOG_TAG, "err processing jobs message: " + je.getMessage());
        }
    }

    /**
     * This method processes the job received from IOTHelper class and updates its status.
     * @param message the job message received as a string
     */
    public void handleJobMessage(String message){
        String packageName = "";
        String appName = "";
        String latestVersion = "";
        String downloadURL = "";
        String jobStatus = "";
        String jobId = "";
        packageManager = context.getPackageManager();
        iotHelper = IOTHelper.getInstance(context);

        if(message != null) {
            try {
                JSONObject jsonMessage = new JSONObject(message);
                JSONObject jobExec = jsonMessage.getJSONObject("execution");
                JSONObject jobDoc = jobExec.getJSONObject("jobDocument");
                Log.i(LOG_TAG, " Job doc received : " + jobDoc.toString());

                packageName = (String)jobDoc.get("packageName");
                latestVersion = jobDoc.getString("latestVersion");
                downloadURL = jobDoc.getString("s3URL");
                appName = jobDoc.getString("appName");
                jobStatus = jobExec.getString("status");
                jobId = jobExec.getString("jobId");

                } catch (JSONException je) {
                    Log.e(LOG_TAG, "json error in job doc : " + je.getMessage());
                }
                boolean isAppInstalled = checkAppAvailability(packageName);
                if(isAppInstalled) {
                    boolean isUpdateReq = checkVersion(packageName, latestVersion);
                    if(isUpdateReq && jobStatus.equals("QUEUED"))
                        downloadAPK(downloadURL, appName, latestVersion, packageName, jobId);
                    //the inprogress job will remain inprogress if isUpdateReq = true.
                    else if(!isUpdateReq)
                        updateJobStatus(IOTConfigData.STATUS_SUCCEEDED, jobId);
                }
                else {
                    Log.d(LOG_TAG, "No such app exists on the device");
                    updateJobStatus(IOTConfigData.STATUS_REJECTED, jobId);
                }
        }
    }

    /**
     * This method updates the status of job according to the status code received by publishing to
     * a topic.
     * @param jobStatus the status code of the jobStatus
     * @param jobId the jobId identifying a job
     */
    private void updateJobStatus(int jobStatus, String jobId){
        Log.i(LOG_TAG, " Updating Job : " + jobId);
        try{
            JSONObject updateRequest = new JSONObject();
            updateRequest.put("clientToken", 123);
            updateRequest.put("stepTimeoutInMinutes", 10000);

            switch (jobStatus){
                case IOTConfigData.STATUS_SUCCEEDED:
                    updateRequest.put("status", "SUCCEEDED");
                    break;
                case IOTConfigData.STATUS_INPROGRESS:
                    updateRequest.put("status", "IN_PROGRESS");
                    break;
                case IOTConfigData.STATUS_QUEUED:
                    updateRequest.put("status", "QUEUED");
                    break;
                case IOTConfigData.STATUS_REJECTED:
                    updateRequest.put("status", "REJECTED");
                    break;
                case IOTConfigData.STATUS_TIMED_OUT:
                    updateRequest.put("status", "TIMED_OUT");
                    break;
                case IOTConfigData.STATUS_FAILED:
                    updateRequest.put("status", "FAILED");
                    break;
                default:
                    Log.d(LOG_TAG, "Unrecognised job status code");
                    break;
            }
            String topic = String.format(IOTConfigData.UPDATE_JOB_PUBLISH_TOPIC, IOTConfigData.DEVICE_ID, jobId);
            iotHelper.publish(updateRequest.toString(), topic);
        }catch (Exception e){
            Log.e(LOG_TAG, "err in updating job status of job : " + jobId + " " + e.getMessage());
        }
    }

    /**
     * This method checks if a given package is installed on the device
     * @param packageName package name of an app.
     * @return a boolean representing whether the package is installed on the device or not.
     */
    private boolean checkAppAvailability(String packageName)
    {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(LOG_TAG, "Package : " + packageName + " not installed");
            return false;
        }
    }

    /**
     * This method checks if a given app requires an update or not by comparing the latest available
     * version and current version installed on the device.
     * @param packageName package name of the app
     * @param latestVersion latest available version of the app
     * @return
     */
    private boolean checkVersion(String packageName, String latestVersion){

        try {
            long currentVersion;
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                 currentVersion = packageInfo.getLongVersionCode();
            else
                currentVersion = (long)packageInfo.versionCode;
            long latestVer = Long.parseLong(latestVersion);
            return latestVer > currentVersion ? true : false;

        }catch (Exception e) {
            Log.e(LOG_TAG, "error checking version : " + e.getMessage());
            return false;
        }
    }

    /**
     * This method downloads the updated apk to an external folder.
     * @param url url for downloading the apk
     * @param appName name of the app
     * @param latestVersion the latest available version
     * @param packageName package name of the app
     */
    private void downloadAPK(final String url,final String appName,
                               final String latestVersion, final String packageName, final String jobId) {

        Log.i(LOG_TAG, "Update available for app : " + appName + ", starting download");
        String subPath = appName + ".apk";
        final String filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath() + File.separator + subPath;
        final File tempFile = new File(filePath);
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileUtils.copyURLToFile(new URL(url), tempFile);
                        onDownloadSuccess(packageName, filePath, latestVersion, jobId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "err : " + e.getMessage());
                        updateJobStatus(IOTConfigData.STATUS_FAILED, jobId);
                        //for anything extra to do, add onDownloadFailed function
                    }
                }
            }).start();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    /**
     * Executed when download apk succeeds. It calls the sendBroadcast and updates the status of job
     * to IN_PROGRESS.
     * @param packageName
     * @param filePath
     * @param latestVersion
     */
    private void onDownloadSuccess(String packageName, String filePath, String latestVersion, String jobId){
        Log.i(LOG_TAG, "apk downloaded to : " + filePath);
        sendBroadcastIntent(packageName, filePath, latestVersion);
        updateJobStatus(IOTConfigData.STATUS_INPROGRESS, jobId);
    }

    /**
     * Method for sending a broadcast intent to the target app.
     * @param packageName package name of the target app
     * @param filePath location of the apk on device
     * @param latestVersion latest available version of target app.
     */
    private void sendBroadcastIntent(String packageName, String filePath, String latestVersion){
        try{
            Intent intent = new Intent();
            intent.setAction("UPDATE_EVENT");
            intent.setPackage(packageName);
            intent.putExtra("AvailableVersion", latestVersion);
            intent.putExtra("FilePath", filePath);
            intent.putExtra("IsUpdateMandatory", "true");
            context.sendBroadcast(intent);
            Log.i(LOG_TAG, "Broadcast sent");
        }catch (Exception e) {
            Log.e("err sending broadcast", e.getMessage());
        }
    }
}
