package org.gunnm.openhomeautomation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.gunnm.openautomation.model.Device;
import org.gunnm.openautomation.model.DeviceStatus;
import org.gunnm.openautomation.model.Event;
import org.gunnm.openautomation.model.Summary;
import org.gunnm.openhomeautomation.activities.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
//import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;


public class RequestTask extends AsyncTask<String, String, String>{
	private Activity 		relatedActivity = null;
	private RequestType 	requestType = null;
	private String 			errMessage = null;
	private ProgressBar 	progressBar = null;
	
	public void setActivity (Activity a)
	{
		this.relatedActivity = a;
	}
	
	public void setRequestType (RequestType rt)
	{
		this.requestType = rt;
	}
	
	public void setProgressBar (ProgressBar pb)
	{
		this.progressBar = pb;
	}
	
	protected void onPreExecute()
	{
		if (progressBar != null)
		{
	        progressBar.setVisibility(View.VISIBLE);
		}
	}
	
	public static Document loadXMLFromString(String xml) throws Exception
	{
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    DocumentBuilder builder = factory.newDocumentBuilder();
	    InputSource is = new InputSource(new StringReader(xml));
	    return builder.parse(is);
	}
	
    protected String doInBackground(String... args) 
    {
    	
    	if (this.relatedActivity == null)
    	{
//    		Log.d ("RequestTask" , "context is null, not trying to issue a request");
    		return null;
    	}
    	if (this.requestType == null)
    	{
//    		Log.d ("RequestTask" , "requesttask is null");
    		return null;
    	}
    	
    	String serverString = PrefsUtils.getHostname (this.relatedActivity);
    	String serverPath = PrefsUtils.getServerPath (this.relatedActivity);
    	String serverUser = PrefsUtils.getServerUser (this.relatedActivity);
    	String serverPass = PrefsUtils.getServerPass (this.relatedActivity);

    	if (serverString == null)
    	{
    		return null;
    	}
    	
    	
  		int port = PrefsUtils.getPort (this.relatedActivity);
  		
    	if (port == 0)
    	{
    		return null;
    	}
  		
  		String url = serverString + ":" + port + "/" + serverPath + "/autocontrol.pl?" + Utils.mapRequestTypeToHttpPost(this.requestType);
//  		Log.d("[RequestTask]", "trying to get " + url);
//  		Log.d("[RequestTask]", "username=" + serverUser);
//  		Log.d("[RequestTask]", "userpass=" + serverPass);
  		DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpResponse response;
        String responseString = null;
        try
        {
            httpclient.getCredentialsProvider().setCredentials (new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), new UsernamePasswordCredentials(serverUser, serverPass));
            response = httpclient.execute(new HttpGet(url));
            StatusLine statusLine = response.getStatusLine();
//            Log.d ("[RequestTask]", "status code" + statusLine.getStatusCode());
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                out.close();
                responseString = out.toString();
            }
            else
            {
                //Closes the connection.
                response.getEntity().getContent().close();
            	errMessage = " http error, code=" + statusLine.getStatusCode();
            }
        } 
        catch (ClientProtocolException e) 
        {
        	errMessage = e.getMessage();
        } 
        catch (IOException e) 
        {
        	errMessage = e.getMessage();
        }
        return responseString;
    }

    @Override
    protected void onPostExecute(String result) {
    	Document xmldoc = null;
    	super.onPostExecute(result);
        
    	if (progressBar != null)
    	{
    		progressBar.setVisibility(View.GONE);
    	}
    	
    	if (result == null)
    	{
    		String msg = "No answer when contacting the server";
    		if (errMessage != null)
    		{
    			msg = errMessage;
    		}
        	Utils.showError(relatedActivity, "Connection Error", msg);
        	return;
    	}

        try
        {
        	xmldoc = loadXMLFromString(result);

        }
        catch (Exception e)
        {
//        	Log.d("RequestTask","result=" + result);
        	Utils.showError(relatedActivity, "Answer error", "Error when parsing the answer");
        	return;
        }
        
    	
    	if (xmldoc.getChildNodes().getLength() != 1)
    	{
    		Utils.showError(relatedActivity, "Request error", "Inconsistent answer");
    		return;
    	}
    	
    	Node node = xmldoc.getChildNodes().item(0);
    	
    	
    	if (node.getNodeName().equalsIgnoreCase("error"))
    	{
    		Node attr = node.getAttributes().getNamedItem("type");
    		if (attr != null)
    		{
//    			Log.d("RequestTask", "BLA" + attr.getNodeValue());
    			if (attr.getNodeValue().equalsIgnoreCase("noserver"))
    			{
    		  		Utils.showError(relatedActivity, "Unavailable", "Server is not available");
    	    		return;
    			}
    			if (attr.getNodeValue().equalsIgnoreCase("invalid-request"))
    			{
    		  		Utils.showError(relatedActivity, "Invalid Request", "Client issues an invalid request");
    	    		return;
    			}
    		}
	  		Utils.showError(relatedActivity, "Unknown Error", "Unknown error, please try again");
    		return;
    	}
    	
    	switch (this.requestType)
        {
        	case GET_GLOBAL_STATE:
        	{
//        		Log.d ("RequestTask", "node name: " + node.getNodeName());
        		Node attr = node.getAttributes().getNamedItem("value");
        		if (attr != null)
        		{
//        			Log.d("RequestTask", "attr val=" + attr.getNodeValue());
        			if (attr.getNodeValue().equalsIgnoreCase("on"))
        			{
        				Utils.setActive(this.relatedActivity);
        			}
        			else
        			{
        				Utils.setInactive(this.relatedActivity);
        			}
        		}
        		break;
        	}
        	
        	case GET_SUMMARY:
        	{
        		
        		Summary summary = new Summary();
//        		Log.d ("RequestTask", "node name: " + node.getNodeName());
        		NodeList children = node.getChildNodes();
        		for (int i = 0 ; i < children.getLength() ; i++)
        		{
        			
        			Node child = children.item(i);
        			Log.d("RequestTask", "child value" + child.getNodeName());
        			if (child.getNodeName().equalsIgnoreCase("device"))
        			{
        				Node attr = null;
        				String typeString = null;
        				String lastEventString = null;
        				String statusString = null;
        				String nameString = null;
        				
        				attr = child.getAttributes().getNamedItem("type");
        				if (attr != null)
        				{
        					typeString = attr.getNodeValue();
        				}
        				
        				attr = child.getAttributes().getNamedItem("status");
        				if (attr != null)
        				{
        					statusString = attr.getNodeValue();
        				}
        				
        				attr = child.getAttributes().getNamedItem("last_event");
        				if (attr != null)
        				{
        					lastEventString = attr.getNodeValue();
        				}
        				
        				attr = child.getAttributes().getNamedItem("name");
        				if (attr != null)
        				{
        					nameString = attr.getNodeValue();
        				}
        				
        				if ((statusString != null) && (lastEventString != null) && (typeString != null) && (nameString != null))
        				{
        					Device dev = new Device (nameString);
        					dev.setStatus(DeviceStatus.OFFLINE);
        					
        					if (statusString.equalsIgnoreCase("on"))
        					{
        						dev.setStatus(DeviceStatus.ONLINE);
        					}
        					
        					
        					Log.d("RequestTask", "Add new device" + nameString);
        					summary.addDevice(dev);
        				}
        			}
        		}
        		
        		if (relatedActivity instanceof Logger)
        		{
        			((Logger)relatedActivity).setSummary (summary);
        			((Logger)relatedActivity).refreshSummaryList();
        		}
        		 break;
        	}
        	
        	case GET_EVENTS:
        	{
        		List<Event> events = new ArrayList<Event>();
//        		Log.d ("RequestTask", "node name: " + node.getNodeName());
        		NodeList children = node.getChildNodes();
        		for (int i = 0 ; i < children.getLength() ; i++)
        		{
        			Node child = children.item(i);
        			Log.d("RequestTask", "child value" + child.getNodeName());
        			if (child.getNodeName().equalsIgnoreCase("event"))
        			{
        				Node attr = null;
        				String detailString = null;
        				String timeString = null;
        				String typeString = null;
        				
        				attr = child.getAttributes().getNamedItem("detail");
        				if (attr != null)
        				{
        					detailString = attr.getNodeValue();
        				}
        				
        				attr = child.getAttributes().getNamedItem("time");
        				if (attr != null)
        				{
        					timeString = attr.getNodeValue();
        				}
        				
        				attr = child.getAttributes().getNamedItem("type");
        				if (attr != null)
        				{
        					typeString = attr.getNodeValue();
        				}
        				
        				Log.d("RequestTask", "detail=" + detailString + ";time=" + timeString + ";type=" + typeString);
        				
        				if ((detailString != null) && (timeString != null) && (typeString != null))
        				{
        					events.add(new Event (typeString,timeString,detailString));
        				}
        			}
        		}
        		
        		if (relatedActivity instanceof Logger)
        		{
        			((Logger)relatedActivity).setEvents (events);
        			((Logger)relatedActivity).refreshEventList();
        		}
        		break;
        	}
        	
        	case SET_GLOBAL_STATE_ON:
        	{
//        		Log.d ("RequestTask", "node name: " + node.getNodeName());
        		Node attr = node.getAttributes().getNamedItem("value");
        		if (attr != null)
        		{
        			if (attr.getNodeValue().equalsIgnoreCase("on"))
        			{
        				Utils.setActive(this.relatedActivity);
        			}
        			else
        			{
        				Utils.showError(relatedActivity, "Error", "Error when trying to activate the server");
        	    		
        				Utils.setInactive(this.relatedActivity);
        			}
        		}
        		break;
        	}
        	case SET_GLOBAL_STATE_OFF:
        	{
//        		Log.d ("RequestTask", "node name: " + node.getNodeName());
        		Node attr = node.getAttributes().getNamedItem("value");
        		if (attr != null)
        		{
        			if (attr.getNodeValue().equalsIgnoreCase("off"))
        			{
        				Utils.setInactive(this.relatedActivity);
        			}
        			else
        			{
        				Utils.showError(relatedActivity, "Error", "Error when trying to deactivate the server");
        	    		
        				Utils.setInactive(this.relatedActivity);
        			}
        		}
        		break;
        	}
        }
    }
}