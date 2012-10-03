package org.rsna.ctp.stdstages;

import java.io.File;

import org.apache.log4j.Logger;
import org.globusonline.transfer.Example;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.json.JSONObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.w3c.dom.Element;

public class GlobusExportService extends AbstractExportService{

	static final Logger logger = Logger.getLogger(GlobusExportService.class);
	//URL url;

	String username = null;
	String password = null;
	boolean authenticate = false;
	String certFile = null;
	String keyFile = null;
	String caFile = null;
	String urlString = null;
	String sourceEP = null;
	String destinationEP = null;
	String destinationRoot = null;
	int transferWaitTime = 300; //In Seconds
	


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
		sourceEP = element.getAttribute("sourceEP");
		destinationEP = element.getAttribute("destinationEP");
		destinationRoot = element.getAttribute("destinationRoot");
		if(!element.getAttribute("transferWaitTime").isEmpty())
		{
			transferWaitTime = Integer.parseInt(element.getAttribute("transferWaitTime")) * 60;
		}

		/**/logger.info(name + " started");

	}
	
	/**
	 * Start the export thread.
	 */
	public void start() {
		startExportThread();
	}

	@Override
	public Status export(File file) {

		//logger.info("1. Inside " + name +"export methods..");
		try {
			//logger.info("2. Inside " + name +"export methods TRY..");	
			//logger.info(username + " : " + caFile + " : " + certFile + " : " + keyFile + " : " + file.getAbsolutePath() + " : " + file.getName() + "\n" );
			//logger.info("WaitTime: " + transferWaitTime);
			
			
			JSONTransferAPIClient client = new JSONTransferAPIClient(username, caFile, certFile, keyFile);        
			org.globusonline.transfer.Example e = new Example(client);

			JSONTransferAPIClient.Result r;
			logger.info("Activating Globus Online Endpoints");
			if (!e.autoActivate(sourceEP) || !e.autoActivate(destinationEP)) {
				logger.error("Unable to auto activate GO endpoints");                               
				return Status.FAIL;
			}
			logger.info("GO Endpoints Activation was successfull..");

			r = client.getResult("/transfer/submission_id");
			String submissionId = r.document.getString("value");

			JSONObject transfer = new JSONObject();
			transfer.put("DATA_TYPE", "transfer");
			transfer.put("submission_id", submissionId);

			JSONObject item = new JSONObject();
			item.put("DATA_TYPE", "transfer_item");
			item.put("source_endpoint", sourceEP);
			item.put("source_path", file.getAbsolutePath());
			item.put("destination_endpoint", destinationEP);
			item.put("destination_path", destinationRoot + file.getName());
			transfer.append("DATA", item);

			r = client.postResult("/transfer", transfer, null);

			String taskId = r.document.getString("task_id");
			
			logger.info("Initiating Globus Online Transfer..");
			logger.info("Waiting " + (transferWaitTime/60) + " minutes for the Globus Transfer to complete");
			if (!e.waitForTask(taskId, 120)) {
				logger.info("Transfer not complete after " + (transferWaitTime/60) + " minutes, exiting!!");
				return Status.FAIL;
			}

		} catch (Exception e) {
			logger.error("Got an exception..");
			e.printStackTrace();
			return Status.FAIL;
		}
		logger.info("Transfer complete..");
		return Status.OK;

	}


}
