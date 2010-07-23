package com.juliangautier.websocket;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


import coldfusion.eventgateway.CFEvent;
import coldfusion.eventgateway.GenericGateway;


public class WebsocketGateway extends GenericGateway {

		private CFWebsocketServer server=null;
		private Properties properties = new Properties();
		public WebsocketGateway(String id,String configPath)
		{
			super(id);
			try
	        {
	            FileInputStream propsFile = new FileInputStream(configPath);
	            properties.load(propsFile);
	            propsFile.close();
	            String port = properties.getProperty("port");
	            String draft= properties.getProperty("draft");
	            if(draft !=null){
	            	this.server=new CFWebsocketServer(Integer.parseInt(port),WebSocketListener.Draft.valueOf(draft));
	            }
	            else
	            {
	            	this.server=new CFWebsocketServer(Integer.parseInt(port),WebSocketListener.Draft.AUTO);
	            }
	            
	        }
	        catch (IOException e)
	        {
	        	
	        }	
		}
		@Override
		protected void startGateway() throws Exception {
			// TODO Auto-generated method stub
			this.server.start();
			
		}

		@Override
		protected void stopGateway() throws Exception {
			// TODO Auto-generated method stub
			this.server.stop();
		}

		@Override
		public String outgoingMessage(CFEvent arg0) {
			// TODO Auto-generated method stub
			return null;
		}
		private class CFWebsocketServer extends WebSocketServer  {
			
			public CFWebsocketServer(int port,Draft draft)
			{
				super(port,draft);
			}
			@Override
			public void onClientClose(WebSocket conn) {
				// TODO Auto-generated method stub
				try {
					CFEvent cfEvent=new CFEvent(getGatewayID());
					cfEvent.setCfcMethod("onClientClose");
					cfEvent.put("connection", conn);
					sendMessage(cfEvent);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			@Override
			public void onClientMessage(WebSocket conn, String message) {
				// TODO Auto-generated method stub
				CFEvent cfEvent=new CFEvent(getGatewayID());
				cfEvent.setCfcMethod("onClientMessage");
				cfEvent.put("connection", conn);
				cfEvent.put("message", message);
				sendMessage(cfEvent);
				

			}

			@Override
			public void onClientOpen(WebSocket conn) {
				// TODO Auto-generated method stub
				try { 
					CFEvent cfEvent=new CFEvent(getGatewayID());
					cfEvent.setCfcMethod("onClientOpen");
					cfEvent.put("connection", conn);
					sendMessage(cfEvent);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

}

