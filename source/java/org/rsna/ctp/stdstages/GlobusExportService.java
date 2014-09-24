package org.rsna.ctp.stdstages;

/*
 * @author Dina Sulakhe <sulakhe@mcs.anl.gov>
 * 10/02/2012
 * Modified by Yaorong Ge 6/10/2014
 */
//import java.io.File;
//import java.io.IOException;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.nio.file.*;
import java.nio.charset.Charset;

import org.apache.log4j.Logger;
import org.globusonline.transfer.APIError;
import org.globusonline.transfer.Example;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.globusonline.transfer.Authenticator;
import org.globusonline.transfer.GoauthAuthenticator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.w3c.dom.Element;

public class GlobusExportService extends AbstractExportService{

	static final Logger logger = Logger.getLogger(GlobusExportService.class);

	JSONTransferAPIClient client;
	org.globusonline.transfer.Example GOClient;

	String username = null;
	String password = null;
	boolean authenticate = false;
	String certFile = null;
	String keyFile = null;
	String caFile = null;
	String urlString = null;

	String goauth = "";
	String goauthTokenFile = "";
	String goauthToken = "";
	String myproxySource = "";
	String myproxyDestination = "";
	String myproxySourceServer = "";
	String myproxyDestinationServer = "";

	String sourceEP = null;
	String sourceUsername = null;
	String sourcePassword = null;	
	String destinationEP = null;
	String destinationUsername = null;
	String destinationPassword = null;
	String destinationRoot = null;
	int transferWaitTime = 3600; //In Seconds
	int filesPerTransfer = 100;
	int numberOfThreads = 1;
	
	Exporter[] exporters = null;
	Exporter exporter = null;

	int successCount = 0;
	int retryCount = 0;

	int throttle = 0;
	
	static final int defaultInterval = 5000;
	static final int minInterval = 1000;
	static final int maxInterval = 2 * defaultInterval;

	int interval = defaultInterval;
	
	static String windowsGCVersion = null;
	

	public GlobusExportService(Element element) throws Exception {
		super(element);
		//Get the credentials, if they are present.
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();

		//urlString = element.getAttribute("url");
		//url = new URL(urlString);
		certFile = element.getAttribute("certfile");
		keyFile = element.getAttribute("keyfile");
		caFile = element.getAttribute("cafile");

		goauth = element.getAttribute("goauth");
		goauthTokenFile = element.getAttribute("goauth-token");
		myproxySource = element.getAttribute("myproxy-source");
		myproxyDestination = element.getAttribute("myproxy-destination");
		myproxySourceServer = element.getAttribute("myproxy-source-server");
		myproxyDestinationServer = element.getAttribute("myproxy-destination-server");

		sourceEP = element.getAttribute("sourceEP");
		sourceUsername = element.getAttribute("sourceUsername");
		sourcePassword = element.getAttribute("sourcePassword");		
		destinationEP = element.getAttribute("destinationEP");
		destinationRoot = element.getAttribute("destinationRoot");
		destinationUsername = element.getAttribute("destinationUsername");
		destinationPassword = element.getAttribute("destinationPassword");
		filesPerTransfer = Integer.parseInt(element.getAttribute("filesPerTransfer"));
		
		if(!element.getAttribute("transferWaitTime").isEmpty())
		{
			transferWaitTime = new Double(Double.parseDouble(element.getAttribute("transferWaitTime")) * 60).intValue();
		}
		if(!element.getAttribute("numberOfThreads").isEmpty())
		{
			numberOfThreads = Integer.parseInt(element.getAttribute("numberOfThreads"));
		}

		if (goauth.equals("yes")){ // yge: read in the token
			try {
	 			byte[] encoded = Files.readAllBytes(Paths.get(goauthTokenFile));
				goauthToken = new String(encoded, Charset.defaultCharset());
				goauthToken = goauthToken.substring(0,goauthToken.length()-1);
				logger.info(goauthToken + "\n");
			} catch(FileNotFoundException e){
				logger.error(e.getStackTrace().toString());
			} catch(IOException e){
				logger.error(e.getStackTrace().toString());
			}
		}

		/*logger.info(name + " started");
		*/

	}
	
	/**
	 * Start the export thread.
	 */
	public void start() {
		//int queueSize = numberOfThreads;
		logger.info("Queue Size: " + numberOfThreads);
		exporters = new Exporter[numberOfThreads];
		
		for(int i=0; i<numberOfThreads; i++){
			exporters[i] = new Exporter();
			exporters[i].start();
			logger.info("Thread " + i + " started..");
		}
		
		//startExportThread();
	/*	exporter = new Exporter();
		exporter.start();
	*/	
		
	}

	private Status activateEP(String EpUsername, String EpPassword, String endpoint){
		
		logger.info("Activating Endpoint: " + endpoint);
		try {
// yge			JSONTransferAPIClient client = new JSONTransferAPIClient(username, caFile, certFile, keyFile);        
// done before the call			org.globusonline.transfer.Example GOClient = new Example(client);

			if(EpUsername == null || EpUsername == ""){
				logger.info("EP username and password is null. Attempting Autoactivations without username/passwd.");
				if (!GOClient.autoActivate(endpoint)) {
					logger.error("Unable to auto activate GO endpoint : " + endpoint);                               
					return Status.FAIL;
				}
			}else{
				if(!GOClient.runPasswordActivation(endpoint, EpUsername, EpPassword )){
					logger.error("Unable to activate GO endpoint : " + endpoint);                               
					return Status.FAIL;					
				}
			}
			return Status.OK;

		} catch (IOException e) {
			logger.error("Got an IO exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());

			e.printStackTrace();
			return Status.FAIL;
		} catch (JSONException e) {
			logger.error("Got an JSON exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
			e.printStackTrace();
			return Status.FAIL;
		} catch (GeneralSecurityException e) {
			logger.error("Got an Security exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
			e.printStackTrace();
			return Status.FAIL;
		} catch (APIError e) {
			logger.error("Got an APIError exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
			e.printStackTrace();
			return Status.FAIL;
		}				

	}


    	private Status myproxyActivateEP(String myproxyUsername,
                                         String myproxyPassphrase,
					 String endpointName,
                                         String myproxyHostname)
	{

		logger.info("Activating Endpoint using myproxy: " + endpointName);

		try {
        		String url = client.endpointPath(endpointName) + "/activation_requirements";
        		JSONTransferAPIClient.Result r = client.getResult(url);

        		// Go through requirements and find the myproxy type, then fill
        		// in with the values from the function^Wmethod parameters.
        		JSONArray reqsArray = r.document.getJSONArray("DATA");
        		for (int i=0; i < reqsArray.length(); i++) {
            			JSONObject reqObject = reqsArray.getJSONObject(i);
            			if (reqObject.getString("type").equals("myproxy")) {
                			String name = reqObject.getString("name");
                			if (name.equals("hostname")) {
                    				reqObject.put("value", myproxyHostname);
                			} else if (name.equals("username")) {
                    				reqObject.put("value", myproxyUsername);
                			} else if (name.equals("passphrase")) {
                    				reqObject.put("value", myproxyPassphrase);
                			}
                		// optional arguments are 'server_dn', required if the hostname
                		// does not match the DN in the server's certificate, and
                		// 'lifetime_in_hours', to ask for a specific lifetime rather
                		// than accepting the server default.
                		// See also:
                		//  https://transfer.api.globusonline.org/v0.10/document_type/activation_requirements/example?format=json
            			}
        		}

        		url = client.endpointPath(endpointName) + "/activate";
        		r = client.postResult(url, r.document);

        		// return r; yge: should check on r to determine what to return
			if (r.statusCode >= 400) {
				logger.error("Returned statusCode >=400 : " + r.statusCode + "\n");
				return Status.FAIL;
			}
			return Status.OK;

		} catch (IOException e) {
			logger.error("Got an IO exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());

			e.printStackTrace();
			return Status.FAIL;
		} catch (JSONException e) {
			logger.error("Got an JSON exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
			e.printStackTrace();
			return Status.FAIL;
		} catch (GeneralSecurityException e) {
			logger.error("Got an Security exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
			e.printStackTrace();
			return Status.FAIL;
		} catch (APIError e) {
			logger.error("Got an APIError exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
			e.printStackTrace();
			return Status.FAIL;
		}			

    	}

	
	@Override
	public Status export(File file) {
		return null;
	}
	
	
	// This may not be needed once the Globus Connect problem on Windows is fixed!! 
	private String checkIfOldWindowsGC(File file){ //return "new" or "old"
		
		if (windowsGCVersion != null){			
			return windowsGCVersion;
		}else{
		
			try{
				JSONTransferAPIClient client = new JSONTransferAPIClient(username, caFile, certFile, keyFile);        
				Map<String, String> pathMap = new HashMap();				
				String[] parentDirSplit = file.getAbsoluteFile().getParent().split(":");
				String parentDir = "/" + parentDirSplit[0].toLowerCase() + parentDirSplit[1].replace("\\", "/");
				pathMap.put("path", parentDir);								
//				JSONTransferAPIClient.Result listing = client.requestDirListing("GET", "/endpoint/" 
				JSONTransferAPIClient.Result listing = client.requestResult("GET", "/endpoint/" 
						+ (sourceEP.contains("#") ? sourceEP.split("#")[1] : sourceEP)+ "/ls", null, pathMap);
				
				if(listing.statusCode == 400){
					logger.info("It might be an older version of GC, Trying cygdrive based configuration..");
					windowsGCVersion = "old";
				}else{
					logger.info("It seems to be a newer version of GC..");
					windowsGCVersion = "new";
				}
				
			} catch (Exception e) {
				logger.error("Got an exception..\n");
				logger.error(e.getMessage());
				logger.error(e.getStackTrace().toString());
			}
		}
		
		return windowsGCVersion;
		
	}
	
	public Status export(List<File> files) {

		//logger.info("1. Inside " + name +"export methods..");
		try {
			
			if (goauth.equals("no")) {
// yge				JSONTransferAPIClient client = new JSONTransferAPIClient(username, caFile, certFile, keyFile);        
				client = new JSONTransferAPIClient(username, caFile, certFile, keyFile);        
			}
			else {
				String baseUrl = null;
				Authenticator authenticator = new GoauthAuthenticator(goauthToken);
// yge         			JSONTransferAPIClient client = new JSONTransferAPIClient(username, null, baseUrl);
            			client = new JSONTransferAPIClient(username, null, baseUrl);
	    			client.setAuthenticator(authenticator);
	    			logger.info("base url: " + client.getBaseUrl());
// yge            			client.endpointDeactivate(sourceEP);
			}

// yge			org.globusonline.transfer.Example GOClient = new Example(client);
			GOClient = new Example(client);

			//Activate source endpoint
			
			Status st;
			if (myproxySource.equals("no")) {
				st = this.activateEP(sourceUsername, sourcePassword, sourceEP); 
			}
			else {
				st = this.myproxyActivateEP(sourceUsername, sourcePassword, sourceEP, myproxySourceServer); 
			}
			if(st.equals(Status.FAIL)){
				logger.info("Source EP Activation failed..");
				return Status.FAIL;
			}
				
			
			//Activate destination endpoint
			
			if (myproxyDestination.equals("no")) {
				st = this.activateEP(destinationUsername, destinationPassword, destinationEP);
			}
			else {
				st = this.myproxyActivateEP(destinationUsername, destinationPassword, destinationEP, 
						myproxyDestinationServer);

			}
			if (st.equals(Status.FAIL)) {
				logger.info("Destination EP activation failed...");
				return Status.FAIL;
			}
			
			logger.info("GO Endpoints Activation was successfull..");
			
			
			
			JSONTransferAPIClient.Result result = client.getResult("/transfer/submission_id");
			String submissionId = result.document.getString("value");

			JSONObject transfer = new JSONObject();
			transfer.put("DATA_TYPE", "transfer");
			transfer.put("submission_id", submissionId);

			//Check if the OS is Windows and translate the Path to GO Compliant 			
			String os = System.getProperty("os.name").toLowerCase();			
			//Uncomment the following line if you want to check the version of Globus Connect on Windows before every transfer!! 
			//windowsGCVersion = null;
			
			
			// Iterate through the files and create a JSON object to be sent to the GO REST API.			
			Iterator<File> iterator = files.iterator();
			while(iterator.hasNext()){
				
				File file = iterator.next();
				String sPath = file.getAbsolutePath();
				if(os.indexOf("win")>= 0){
					
					//Preparing the source path by removing : on Windows. 
					String[] sPathSplit = sPath.split(":");
					sPath = "/"+ sPathSplit[0].toLowerCase() + sPathSplit[1].replace("\\","/");
					
					//Check if this source path is accessible on GO, if not assume it is cygdrive
					// version of the GC and append /cygdrive to path.
					// The rest of the code in this if-loop should be deleted once GC bug is fixed.
					if(this.checkIfOldWindowsGC(file).equals("old")){
						//logger.info("It might be an older version of GC, Trying cygdrive based configuration..");
						sPath = "/cygdrive" + sPath;
					}
					
				}

				logger.info("File to be transferred: " + sPath);
				JSONObject item = new JSONObject();
				item.put("DATA_TYPE", "transfer_item");
				item.put("source_endpoint", sourceEP);
				item.put("source_path", sPath);
				item.put("destination_endpoint", destinationEP);
				item.put("destination_path", destinationRoot + file.getName());

				logger.info("File to be transferred to at destination: " + destinationRoot + file.getName());
				transfer.append("DATA", item);
			
			}

			
			result = client.postResult("/transfer", transfer, null);
			String taskId = result.document.getString("task_id");
			
			logger.info("Initiating Globus Online Transfer..");
			if (transferWaitTime < 60) {
				logger.info("Waiting " + transferWaitTime + " seconds for the Globus Transfer to complete");
			} else {
				logger.info("Waiting " + (transferWaitTime/60) + " minutes for the Globus Transfer to complete");
			}
			if (!GOClient.waitForTask(taskId, transferWaitTime)) {
				if (transferWaitTime < 60) {
					logger.info("Transfer not complete after " + transferWaitTime + " seconds, exiting!!");
				} else {
					logger.info("Transfer not complete after " + (transferWaitTime/60) + " minutes, exiting!!");
				}
				return Status.FAIL;
			}

		} catch (Exception e) {
			logger.error("Got an exception..\n");
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
			
			e.printStackTrace();
			return Status.FAIL;
		}
		logger.info("Transfer complete..");
		return Status.OK;

	}
	
	//Exporter thread that actually gets launched and that handles the transfers.
	
	class Exporter extends Thread {
		public Exporter() {
			super(name + " Exporter");
			logger.info("New Exporter class..");
		}
		public void run() {
			logger.info(name+": Exporter Thread: Started");
			File file = null;
			while (!stop && !interrupted()) {
				try {
					if ((getQueueSize()>0) && connect().equals(Status.OK)) {
						
						logger.info("Total files in Queue: " + getQueueSize());
						
						List<File> files = new ArrayList<File>();
						
						
						while (!stop && ((files.size() < filesPerTransfer) && (file = getNextFile()) != null)) {
							files.add(file);
						}
						Status result = export(files);
						if (result.equals(Status.FAIL)) {
							//Something is wrong with the file.
							//Log a warning and quarantine the file.
							logger.warn(name+": Unable to export "+file);
							if (quarantine != null) quarantine.insert(file);
							else file.delete();
						}
						else if (result.equals(Status.RETRY)) {
							
							for(int i=0; i<files.size(); i++){
								file = files.get(i);
								//Something is wrong, but probably not with the file.
								//Note that the file has been removed from the queue,
								//so it is necessary to requeue it. This has the
								//effect of moving it to the end of the queue.
								getQueueManager().enqueue(file);
								//Note that enqueuing a file does not delete it
								//from the source location, so we must delete it now.
								file.delete();
							}
							logger.debug("Status.RETRY received: successCount = "+successCount+"; retryCount = "+retryCount);
							successCount = 0;
							//Only break if we have had a string of failures
							//in a row; otherwise, move on to the next file.
							if (retryCount++ > 5) break;
						}
						else {
							if (throttle > 0) {
								try { Thread.sleep(throttle); }
								catch (Exception ignore) { }
							}
							for(int i=0; i<files.size(); i++){
								file = files.get(i);							
								release(file);
								successCount++;
							}
							retryCount = 0;
						}
						disconnect();
					}
					if (!stop) sleep(interval);
					//Recount the queue in case it has been corrupted by
					//someone copying files into the queue directories by hand.
					//To keep from doing this when it doesn't really matter and
					//it might take a long time, only do it when the remaining
					//queue is small.
					if (!stop && (getQueueSize() < 20)) recount();
				}
				catch (Exception e) {
					logger.warn(name+" Exporter Thread: Exception received",e);
					stop = true;
				}
			}
			logger.info(name+" Thread: Interrupt received; thread stopped");
		}
	}


}
